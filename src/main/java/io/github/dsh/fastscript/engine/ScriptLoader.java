package io.github.dsh.fastscript.engine;

import io.github.dsh.fastscript.ast.Ast;
import io.github.dsh.fastscript.compiler.Compiler;
import io.github.dsh.fastscript.core.ScriptException;
import io.github.dsh.fastscript.lang.Parser;
import io.github.dsh.fastscript.runtime.FunctionSpec;
import io.github.dsh.fastscript.runtime.ScriptHandle;
import io.github.dsh.fastscript.runtime.ScriptSource;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles and loads script sources.
 *
 * <p>Loading runs the whole pipeline: parse, compile to bytecode, define the class, and
 * resolve every handler to a {@link MethodHandle}. Resolution happens once at load time, so
 * event dispatch costs no reflective lookup and no boxing of the handler arguments.</p>
 *
 * <p>Compilation is split into phases across all sources. All classes are generated
 * one script at a time; every script keeps its own function table, so a function
 * is visible only inside the file that declares it. Sharing across files is done
 * through events, commands and global variables.</p>
 */
public final class ScriptLoader {

    private static final MethodType HANDLER_TYPE =
            MethodType.methodType(void.class, Object[].class, io.github.dsh.fastscript.runtime.ExecContext.class);

    private final String rootPackage;

    /**
     * @param rootPackage package for generated classes, for example
     *                    {@code io.github.dsh.fastscript.compiled}
     */
    public ScriptLoader(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    /** A compiled script together with the resolved entry points. */
    public record Loaded(ScriptSource source, Ast.Script parsed, ScriptHandle handle,
            Map<String, MethodHandle> triggers, Map<String, MethodHandle> commands,
            Map<String, MethodHandle> functions) {

        public MethodHandle trigger(String eventName) {
            return triggers.get(eventName);
        }

        public MethodHandle command(String name) {
            return commands.get(name);
        }
    }

    /** Raised when a script cannot be compiled. Carries the parse error when applicable. */
    public static final class LoadException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final transient ScriptSource source;

        LoadException(ScriptSource source, String message, Throwable cause) {
            super(message, cause);
            this.source = source;
        }

        public ScriptSource source() {
            return source;
        }
    }

    /** Compiles and loads a batch of scripts, returning one entry per source in input order. */
    public List<Loaded> load(List<ScriptSource> sources) {
        Map<String, byte[]> bytecode = new LinkedHashMap<>();
        Map<String, Ast.Script> parsed = new LinkedHashMap<>();
        List<LoadException> failures = new ArrayList<>();
        // Set FASTSCRIPT_DUMP_DIR to keep generated classes on disk for javap inspection.
        String dumpDir = System.getenv("FASTSCRIPT_DUMP_DIR");
        Path dumpPath = dumpDir == null || dumpDir.isBlank() ? null : Path.of(dumpDir);

        for (ScriptSource source : sources) {
            try {
                Ast.Script script = Parser.parse(source.source(), source.fileName());
                Compiler compiler = new Compiler(script, classFor(source), className(source),
                        source.fileName());
                // Keyed by binary name so the child loader can resolve exactly one definition
                // per generated class, which is what defineClass requires.
                byte[] bytes = compiler.compile();
                bytecode.put(className(source), bytes);
                parsed.put(source.id(), script);
                if (dumpPath != null) {
                    Path target = dumpPath.resolve(className(source).replace('.', '/') + ".class");
                    Files.createDirectories(target.getParent());
                    Files.write(target, bytes);
                }
            } catch (ScriptException error) {
                failures.add(new LoadException(source, error.render(), error));
            } catch (IOException error) {
                failures.add(new LoadException(source, "cannot dump bytecode: " + error, error));
            } catch (RuntimeException error) {
                failures.add(new LoadException(source, source.fileName() + ": " + error.getMessage(), error));
            }
        }

        ScriptClassLoader classLoader = new ScriptClassLoader(getClass().getClassLoader(), bytecode);
        List<Loaded> loaded = new ArrayList<>();
        for (ScriptSource source : sources) {
            byte[] bytes = bytecode.get(className(source));
            if (bytes == null) {
                continue;
            }
            try {
                loaded.add(define(source, parsed.get(source.id()), bytes, classLoader));
            } catch (ReflectiveOperationException | RuntimeException error) {
                failures.add(new LoadException(source, source.fileName() + ": cannot load compiled script: "
                        + error, error));
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder("failed to load " + failures.size() + " script(s)");
            for (LoadException failure : failures) {
                message.append('\n').append("  ").append(failure.getMessage());
            }
            throw new LoadException(failures.get(0).source(), message.toString(), failures.get(0).getCause());
        }
        return loaded;
    }

    private Loaded define(ScriptSource source, Ast.Script parsed, byte[] bytecode,
            ScriptClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> compiled = classLoader.define(className(source), bytecode);
        MethodHandle factory = MethodHandles.publicLookup().findStatic(compiled, "create",
                MethodType.methodType(ScriptHandle.class));
        ScriptHandle handle;
        try {
            handle = (ScriptHandle) factory.invoke();
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new ReflectiveOperationException("script factory failed: " + error, error);
        }
        handle.bindClass(compiled);

        Map<String, MethodHandle> triggers = new LinkedHashMap<>();
        Map<String, MethodHandle> commands = new LinkedHashMap<>();
        for (var entry : handle.triggers().entrySet()) {
            triggers.put(entry.getKey(), lookup(compiled, entry.getValue().methodName()));
        }
        for (var entry : handle.commands().entrySet()) {
            commands.put(entry.getKey(), lookup(compiled, entry.getValue().methodName()));
        }
        Map<String, MethodHandle> functions = new LinkedHashMap<>();
        for (var entry : handle.functions().entrySet()) {
            FunctionSpec spec = entry.getValue();
            functions.put(entry.getKey(), MethodHandles.publicLookup()
                    .findStatic(compiled, spec.methodName(), MethodType.fromMethodDescriptorString(
                            spec.descriptor(), getClass().getClassLoader())));
        }
        return new Loaded(source, parsed, handle, Map.copyOf(triggers), Map.copyOf(commands),
                Map.copyOf(functions));
    }

    private MethodHandle lookup(Class<?> owner, String methodName) throws ReflectiveOperationException {
        return MethodHandles.publicLookup().findStatic(owner, methodName, HANDLER_TYPE);
    }

    private String className(ScriptSource source) {
        return rootPackage + ".Script_" + sanitize(source.id());
    }

    private String classFor(ScriptSource source) {
        return className(source).replace('.', '/');
    }

    private static String sanitize(String value) {
        // Same reversible scheme as the compiler: '_' doubles, exotic characters
        // become '$' + hex, so ids like `a-b` and `a_b` define distinct classes.
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(character);
            } else if (character == '_') {
                builder.append("__");
            } else {
                builder.append('$').append(Integer.toHexString(character));
            }
        }
        if (builder.isEmpty() || Character.isDigit(builder.charAt(0))) {
            builder.insert(0, 'S');
        }
        return builder.toString();
    }

    /**
     * Defines generated classes inside a child loader.
     *
     * <p>The parent is the server's plugin class loader, so generated code shares the same view
     * of Bukkit and of this plugin's own runtime classes.</p>
     */
    static final class ScriptClassLoader extends ClassLoader {

        private final Map<String, byte[]> definitions;

        ScriptClassLoader(ClassLoader parent, Map<String, byte[]> definitions) {
            super("fastscript-scripts", parent == null ? getSystemClassLoader() : parent);
            this.definitions = definitions;
        }

        Class<?> define(String binaryName, byte[] bytecode) {
            return defineClass(binaryName, bytecode, 0, bytecode.length);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
}
