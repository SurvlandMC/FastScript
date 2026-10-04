package io.github.dsh.fastscript.compiler;

import io.github.dsh.fastscript.ast.Ast;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of builtin functions.
 *
 * <p>Entries here compile to a direct {@code INVOKESTATIC} or {@code INVOKEVIRTUAL} with fully
 * typed arguments whenever the compiler can prove the argument types: no argument array, no
 * name lookup, no reflection. Calls whose argument types are dynamic fall back to the variadic
 * dispatcher in {@link io.github.dsh.fastscript.runtime.Functions}.</p>
 */
public final class Builtins {

    /** JVM internal names used by generated calls. */
    public static final String CONTEXT_OWNER = "io/github/dsh/fastscript/runtime/ExecContext";
    public static final String FUNCTIONS_OWNER = "io/github/dsh/fastscript/runtime/Functions";

    /** Descriptor of the shared variadic fallback entry point. */
    public static final String DISPATCH_DESCRIPTOR =
            "(L" + CONTEXT_OWNER + ";[Ljava/lang/Object;)Ljava/lang/Object;";

    /**
     * A single builtin entry.
     *
     * @param methodDescriptor JVM method descriptor of the target method
     * @param returnType       script type produced by the call
     * @param parameterTypes   script types required for the inline fast path; empty when variadic
     * @param staticCall       true for {@code INVOKESTATIC}, false for a context method
     * @param variadic         true when the call must go through the dispatcher
     */
    public record BuiltinSpec(String methodDescriptor, Ast.Type returnType, Ast.Type[] parameterTypes,
            boolean staticCall, boolean variadic) {

        public int arity() {
            return parameterTypes.length;
        }
    }

    private static final Map<String, BuiltinSpec> SPECS = new LinkedHashMap<>();
    /** Names (and name/arity pairs) known to the run-time dispatcher. */
    private static final Set<String> DYNAMIC = new LinkedHashSet<>();

    private static void put(String name, String descriptor, Ast.Type returnType, boolean staticCall,
            Ast.Type... parameters) {
        SPECS.put(name, new BuiltinSpec(descriptor, returnType, parameters, staticCall, false));
    }

    static {
        // --- messaging -----------------------------------------------------------------
        put("message", "(Ljava/lang/String;)V", Ast.Type.VOID, false, Ast.Type.TEXT);
        put("broadcast", "(Ljava/lang/String;)V", Ast.Type.VOID, false, Ast.Type.TEXT);
        put("log", "(Ljava/lang/String;)V", Ast.Type.VOID, false, Ast.Type.TEXT);
        put("title", "(Ljava/lang/String;Ljava/lang/String;)V", Ast.Type.VOID, false,
                Ast.Type.TEXT, Ast.Type.TEXT);
        put("kick", "(Ljava/lang/String;)V", Ast.Type.VOID, false, Ast.Type.TEXT);
        put("sound", "(Ljava/lang/String;)V", Ast.Type.VOID, false, Ast.Type.TEXT);

        // --- player state --------------------------------------------------------------
        put("damage", "(D)V", Ast.Type.VOID, false, Ast.Type.NUMBER);
        put("heal", "(D)V", Ast.Type.VOID, false, Ast.Type.NUMBER);
        put("kill", "()V", Ast.Type.VOID, false);
        put("teleport", "(DDD)V", Ast.Type.VOID, false,
                Ast.Type.NUMBER, Ast.Type.NUMBER, Ast.Type.NUMBER);
        put("give", "(Ljava/lang/String;D)V", Ast.Type.VOID, false, Ast.Type.TEXT, Ast.Type.NUMBER);

        // --- control -------------------------------------------------------------------
        put("stop", "()V", Ast.Type.VOID, false);
        put("cancel-event", "()V", Ast.Type.VOID, false);

        // --- pure helpers --------------------------------------------------------------
        put("random", "(DD)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER, Ast.Type.NUMBER);
        put("min", "(DD)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER, Ast.Type.NUMBER);
        put("max", "(DD)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER, Ast.Type.NUMBER);
        put("floor", "(D)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER);
        put("ceil", "(D)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER);
        put("round", "(D)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER);
        put("abs", "(D)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER);
        put("sqrt", "(D)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER);
        put("pow", "(DD)D", Ast.Type.NUMBER, true, Ast.Type.NUMBER, Ast.Type.NUMBER);
        put("length", "(Ljava/lang/Object;)D", Ast.Type.NUMBER, true, Ast.Type.ANY);
        put("upper", "(Ljava/lang/Object;)Ljava/lang/String;", Ast.Type.TEXT, true, Ast.Type.ANY);
        put("lower", "(Ljava/lang/Object;)Ljava/lang/String;", Ast.Type.TEXT, true, Ast.Type.ANY);
        put("join", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", Ast.Type.TEXT, true,
                Ast.Type.ANY, Ast.Type.ANY);

        // --- context queries (receiver is the ExecContext) ------------------------------
        put("sender", "()Ljava/lang/Object;", Ast.Type.ANY, false);
        put("sender-name", "()Ljava/lang/String;", Ast.Type.TEXT, false);
        put("arg", "(D)Ljava/lang/String;", Ast.Type.TEXT, false, Ast.Type.NUMBER);
        put("online-players", "()Ljava/util/List;", Ast.Type.LIST, false);
    }

    private Builtins() {
    }

    /** Declares names handled by the run-time dispatcher; called once during plugin start-up. */
    public static void registerDynamic(Collection<String> names) {
        DYNAMIC.addAll(names);
    }

    /**
     * Resolves a call.
     *
     * @return the inline entry point, a variadic dispatcher entry, or {@code null} when the
     *         function is unknown and compilation should report an error
     */
    public static BuiltinSpec lookup(String name, int arity) {
        BuiltinSpec exact = SPECS.get(name);
        if (exact != null && exact.arity() == arity) {
            return exact;
        }
        if (DYNAMIC.contains(name) || DYNAMIC.contains(name + "/" + arity)) {
            return dispatchSpec();
        }
        return null;
    }

    /** True when the name exists with any arity; used to improve error messages. */
    public static boolean known(String name) {
        if (SPECS.containsKey(name)) {
            return true;
        }
        return DYNAMIC.stream().anyMatch(entry -> entry.equals(name) || entry.startsWith(name + "/"));
    }

    private static BuiltinSpec dispatchSpec() {
        return new BuiltinSpec(DISPATCH_DESCRIPTOR, Ast.Type.ANY, new Ast.Type[0], true, true);
    }

    public static Map<String, BuiltinSpec> all() {
        return Map.copyOf(SPECS);
    }

    public static Set<String> dynamicNames() {
        return Set.copyOf(DYNAMIC);
    }
}
