package io.github.dsh.fastscript.engine;

import io.github.dsh.fastscript.core.EvalException;
import io.github.dsh.fastscript.core.Values;
import io.github.dsh.fastscript.engine.ScriptLoader.LoadException;
import io.github.dsh.fastscript.engine.ScriptLoader.Loaded;
import io.github.dsh.fastscript.runtime.CommandSpec;
import io.github.dsh.fastscript.runtime.ExecContext;
import io.github.dsh.fastscript.runtime.FunctionSpec;
import io.github.dsh.fastscript.runtime.Functions;
import io.github.dsh.fastscript.runtime.Host;
import io.github.dsh.fastscript.runtime.ScriptSource;
import io.github.dsh.fastscript.runtime.VariableStore;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.bukkit.entity.Player;

/**
 * Keeps the set of loaded scripts and dispatches events and commands to them.
 *
 * <p>Scripts are compiled once at load time, so dispatch is a {@link MethodHandle} invocation
 * per interested handler, with a reusable {@link ExecContext} per script: steady-state event
 * handling performs no reflective lookups, no name resolution and no context allocation.</p>
 */
public final class ScriptEngine {

    private static final int ERROR_HISTORY = 100;
    private static final MethodType HANDLER_TYPE =
            MethodType.methodType(void.class, Object[].class, ExecContext.class);

    private final Host host;
    private final ScriptLoader loader = new ScriptLoader("io.github.dsh.fastscript.compiled");
    private final Map<String, Loaded> scripts = new LinkedHashMap<>();
    private final Map<String, List<Handler>> byEvent = new LinkedHashMap<>();
    private final Map<String, List<Handler>> byCommand = new LinkedHashMap<>();
    private final Map<String, ExecContext> contexts = new LinkedHashMap<>();
    private final Deque<String> errors = new ArrayDeque<>();
    private final MethodHandle invoker;

    /** A compiled handler together with the script that owns it. */
    private record Handler(Loaded script, MethodHandle method) {
    }

    public ScriptEngine(Host host) {
        this.host = host;
        this.invoker = MethodHandles.spreadInvoker(HANDLER_TYPE, 0);
        Functions.registerCompilerEntries();
    }

    // ------------------------------------------------------------------ loading

    /** Loads every {@code .fs} file under {@code directory}, replacing the current set. */
    public LoadResult loadDirectory(Path directory) {
        List<ScriptSource> sources = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            errors.add("script directory does not exist: " + directory);
            return load(sources);
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".fs"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path file : files) {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                sources.add(new ScriptSource(relative.substring(0, relative.length() - 3),
                        file.getFileName().toString(),
                        Files.readString(file, StandardCharsets.UTF_8), file.toString()));
            }
        } catch (IOException error) {
            errors.add("cannot read scripts in " + directory + ": " + error.getMessage());
        }
        return load(sources);
    }

    /** Replaces the loaded script set. Failures are collected instead of thrown. */
    public LoadResult load(List<ScriptSource> sources) {
        byEvent.clear();
        byCommand.clear();
        contexts.clear();
        scripts.clear();

        List<Loaded> loaded = List.of();
        try {
            loaded = loader.load(sources);
        } catch (LoadException error) {
            errors.add(error.getMessage());
        }

        for (Loaded script : loaded) {
            String id = script.handle().id();
            scripts.put(id, script);
            contexts.put(id, new ExecContext(host, null, null, new Object[0], id, ""));
            for (Map.Entry<String, MethodHandle> entry : script.triggers().entrySet()) {
                byEvent.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                        .add(new Handler(script, entry.getValue()));
            }
            for (Map.Entry<String, MethodHandle> entry : script.commands().entrySet()) {
                byCommand.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                        .add(new Handler(script, entry.getValue()));
            }
        }
        return new LoadResult(scripts.size(), sources.size(), List.copyOf(errors));
    }

    /**
     * @param loaded number of successfully compiled scripts
     * @param total  number of sources discovered
     * @param errors human readable failures
     */
    public record LoadResult(int loaded, int total, List<String> errors) {

        public boolean successful() {
            return errors.isEmpty() && loaded == total;
        }
    }

    // ------------------------------------------------------------------ dispatch

    /** Invokes every handler registered for an event. */
    public void dispatch(String eventName, Player player, Object event) {
        List<Handler> handlers = byEvent.get(eventName);
        if (handlers == null) {
            return;
        }
        Object[] arguments = {player, event};
        for (Handler handler : handlers) {
            invoke(handler, arguments, player, null);
        }
    }

    /** Invokes every handler registered for a command label. */
    public void dispatchCommand(String label, Object[] arguments) {
        List<Handler> handlers = byCommand.get(label.toLowerCase(java.util.Locale.ROOT));
        if (handlers == null) {
            return;
        }
        Player player = arguments.length > 0 && arguments[0] instanceof Player candidate ? candidate : null;
        // Command arguments become the handler's argument array: args[0] is the sender, the
        // rest are the positional arguments, so they can be appended without copying later.
        Object[] handlerArguments = new Object[arguments.length + 1];
        handlerArguments[0] = player;
        handlerArguments[1] = null;
        System.arraycopy(arguments, 0, handlerArguments, 2, arguments.length);
        for (Handler handler : handlers) {
            invoke(handler, handlerArguments, player, arguments);
        }
    }

    /** Fires handlers for a parameterless event such as {@code server start}. */
    public void fire(String eventName) {
        dispatch(eventName, null, null);
    }

    private void invoke(Handler handler, Object[] handlerArguments, Player player, Object[] commandArguments) {
        ExecContext context = contexts.get(handler.script().handle().id());
        if (context == null) {
            return;
        }
        context.reset();
        if (commandArguments == null) {
            context.setArguments(handlerArguments);
            context.setEvent(handlerArguments.length > 1 ? handlerArguments[1] : null);
        } else {
            // The generated code reads arguments through ctx.arg(n), which addresses the
            // context array directly, so command arguments must live there verbatim.
            context.setArguments(handlerArguments);
            context.setEvent(null);
        }
        context.setPlayer(player);
        context.setHandler(handler.method());
        context.setHandlerName(handler.script().handle().sourceName());
        try {
            invoker.invokeExact(handlerArguments, context);
        } catch (EvalException error) {
            report(handler, error.getMessage());
        } catch (Throwable error) {
            report(handler, String.valueOf(error));
        }
    }

    private void report(Handler handler, String message) {
        String text = handler.script().handle().sourceName() + ": " + message;
        synchronized (errors) {
            errors.addLast(text);
            while (errors.size() > ERROR_HISTORY) {
                errors.removeFirst();
            }
        }
        host.log("[FastScript] " + text);
    }

    /**
     * Runs a script function directly.
     *
     * <p>This is the generic entry point for the API, admin commands and benchmarks, so it
     * accepts boxed arguments and boxes the primitive result. Event dispatch never comes
     * through here: that path uses pre-resolved {@link MethodHandle}s.</p>
     */
    public Object callFunction(Loaded script, String functionName, ExecContext context, Object... arguments) {
        FunctionSpec spec = script.handle().functions().get(functionName);
        if (spec == null) {
            throw new EvalException("script '" + script.handle().id() + "' has no function '" + functionName + "'");
        }
        MethodHandle handle = script.functions().get(functionName);
        if (handle == null) {
            throw new EvalException("function '" + functionName + "' is not callable");
        }
        Class<?>[] parameterTypes = handle.type().parameterArray();
        Object[] values = new Object[parameterTypes.length];
        for (int index = 0; index < values.length; index++) {
            Object argument = index < arguments.length ? arguments[index] : null;
            values[index] = coerce(parameterTypes[index], argument);
        }
        try {
            return handle.invokeWithArguments(values);
        } catch (EvalException error) {
            throw error;
        } catch (Throwable error) {
            throw new EvalException("function '" + functionName + "' failed: " + error, error);
        }
    }

    /** Converts a script value to the JVM type a generated function expects. */
    private Object coerce(Class<?> type, Object value) {
        if (type == double.class) {
            return Values.toNumber(value);
        }
        if (type == boolean.class) {
            return Values.toBool(value);
        }
        if (type == String.class) {
            return Values.toText(value);
        }
        return value;
    }

    // ------------------------------------------------------------------ accessors

    public Map<String, Loaded> scripts() {
        return Map.copyOf(scripts);
    }

    public Set<String> eventNames() {
        return new LinkedHashSet<>(byEvent.keySet());
    }

    public Set<String> commandNames() {
        return new LinkedHashSet<>(byCommand.keySet());
    }

    public List<String> errors() {
        synchronized (errors) {
            return List.copyOf(errors);
        }
    }

    public List<CommandSpec> commandSpecs() {
        List<CommandSpec> specs = new ArrayList<>();
        for (Loaded script : scripts.values()) {
            specs.addAll(script.handle().commands().values());
        }
        specs.sort(Comparator.comparing(CommandSpec::name));
        return specs;
    }

    public int triggerCount() {
        return byEvent.values().stream().mapToInt(List::size).sum();
    }

    public int compiledClassCount() {
        return scripts.size();
    }

    public VariableStore variables() {
        return host.variables();
    }

    public Host host() {
        return host;
    }
}
