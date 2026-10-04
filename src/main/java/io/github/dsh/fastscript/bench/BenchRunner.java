package io.github.dsh.fastscript.bench;

import io.github.dsh.fastscript.core.Values;
import io.github.dsh.fastscript.engine.ScriptEngine;
import io.github.dsh.fastscript.engine.ScriptLoader;
import io.github.dsh.fastscript.engine.ScriptLoader.Loaded;
import io.github.dsh.fastscript.runtime.ExecContext;
import io.github.dsh.fastscript.runtime.Host;
import io.github.dsh.fastscript.runtime.ScriptSource;
import io.github.dsh.fastscript.runtime.VariableStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Executes compiled scripts without a Minecraft server.
 *
 * <p>This is both the engine's self-test and the performance harness: it loads a script,
 * invokes typed functions directly through their {@code MethodHandle}s, and compares the
 * specialised bytecode against a tree-walking interpreter — the execution model used by
 * classic script engines such as Skript.</p>
 *
 * <p>Usage: {@code Bench <scriptFile> [iterations]}</p>
 */
public final class BenchRunner {

    // ------------------------------------------------------------------ fake host

    /** Records side effects so behaviour can be asserted without a server. */
    static final class RecordingHost implements Host {

        final List<String> messages = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final VariableStore store = new VariableStore();
        int messageCount;

        @Override
        public void message(Player player, String text) {
            messageCount++;
            if (messages.size() < 8) {
                messages.add(text);
            }
        }

        @Override
        public void broadcast(String text) {
            logs.add("broadcast: " + text);
        }

        @Override
        public void log(String text) {
            logs.add(text);
        }

        @Override
        public void damage(Player player, double amount) {
            logs.add("damage " + Values.format(amount));
        }

        @Override
        public void heal(Player player, double amount) {
            logs.add("heal " + Values.format(amount));
        }

        @Override
        public void kill(Player player) {
            logs.add("kill");
        }

        @Override
        public void teleport(Player player, double x, double y, double z) {
            logs.add("teleport " + Values.format(x));
        }

        @Override
        public void giveItem(Player player, String material, int amount) {
            logs.add("give " + material + " x" + amount);
        }

        @Override
        public void playSound(Player player, String sound) {
            logs.add("sound " + sound);
        }

        @Override
        public void sendTitle(Player player, String title, String subtitle) {
            logs.add("title " + title + " / " + subtitle);
        }

        @Override
        public void kick(Player player, String reason) {
            logs.add("kick " + reason);
        }

        @Override
        public void setBlock(String world, int x, int y, int z, String material) {
            logs.add("block " + material + " @ " + x + "," + y + "," + z);
        }

        @Override
        public void runLater(Runnable task, long delayTicks) {
            task.run();
        }

        @Override
        public void cancelEvent() {
            logs.add("cancelled");
        }

        @Override
        public VariableStore variables() {
            return store;
        }

        @Override
        public Map<String, Object> scriptInfo() {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ runner

    /** Outcome of the self-test phase. */
    private record Check(String name, boolean passed, String detail) {
    }

    public static void main(String[] args) throws Exception {
        Path script = Path.of(args.length > 0 ? args[0] : "bench/scripts/Arithmetic.fs");
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 200_000;

        if (!Files.isRegularFile(script)) {
            System.err.println("script not found: " + script.toAbsolutePath());
            System.exit(2);
        }

        ScriptSource source = new ScriptSource("bench", script.getFileName().toString(),
                Files.readString(script, StandardCharsets.UTF_8), script.toString());
        RecordingHost host = new RecordingHost();
        ScriptLoader loader = new ScriptLoader("io.github.dsh.fastscript.testcompiled");
        List<Loaded> loaded = loader.load(List.of(source));
        if (loaded.isEmpty()) {
            System.err.println("script did not compile");
            System.exit(1);
        }
        Loaded compiled = loaded.get(0);

        List<Check> checks = new ArrayList<>();
        runCorrectnessChecks(compiled, host, checks);

        System.out.println();
        System.out.println("=== correctness ===");
        boolean allPassed = true;
        for (Check check : checks) {
            System.out.printf("%-28s %s   %s%n", check.name(), check.passed() ? "PASS" : "FAIL",
                    check.detail());
            allPassed &= check.passed();
        }

        System.out.println();
        System.out.println("=== bytecode overview ===");
        describeCompilation(compiled);

        System.out.println();
        System.out.println("=== engine throughput ===");
        benchmark(compiled, iterations);

        if (!allPassed) {
            System.out.println();
            System.out.println("FAILURES PRESENT");
            System.exit(1);
        }
        System.out.println();
        System.out.println("all checks passed");
    }

    private static void runCorrectnessChecks(Loaded script, RecordingHost host, List<Check> checks) {
        ScriptEngine engine = new ScriptEngine(host);
        ExecContext context = new ExecContext(host, null, null, new Object[0],
                script.handle().id(), "bench");

        // fib(20) = 6765 exercises loops, conditionals and typed returns.
        context.reset();
        Object fib = engine.callFunction(script, "fib", context, 20.0);
        checks.add(new Check("fib(20)", Values.toNumber(fib) == 6765.0, "got " + Values.toText(fib)));

        // sum-range(10) = 0+1+...+9 = 45 exercises loop-index.
        for (double limit : new double[] {10.0, 3.0, 4.0}) {
            context.reset();
            Object sum = engine.callFunction(script, "sum-range", context, limit);
            double expected = limit * (limit - 1) / 2;
            checks.add(new Check("sum-range(" + Values.format(limit) + ")",
                    Values.toNumber(sum) == expected, "got " + Values.toText(sum)
                            + " expected " + Values.format(expected)));
        }

        // echo-number checks argument passing and primitive returns in isolation.
        context.reset();
        Object echoed = engine.callFunction(script, "echo-number", context, 7.5);
        checks.add(new Check("echo-number(7.5)", Values.toNumber(echoed) == 7.5,
                "got " + Values.toText(echoed)));

        // sum-count checks that the loop honours its count without touching loop-index.
        context.reset();
        Object iterations = engine.callFunction(script, "sum-count", context, 5.0);
        checks.add(new Check("sum-count(5)", Values.toNumber(iterations) == 5.0,
                "got " + Values.toText(iterations)));

        // countdown(3) exercises text concatenation across mixed operand types.
        context.reset();
        Object text = engine.callFunction(script, "countdown", context, 3.0);
        String expected = "3,2,1,";
        checks.add(new Check("countdown(3)", expected.equals(Values.toText(text)),
                "got '" + Values.toText(text) + "'"));

        // Handlers must be registered with their metadata.
        int functionCount = script.handle().functions().size();
        checks.add(new Check("handler registration",
                functionCount == 5 && script.handle().commands().isEmpty()
                        && script.handle().triggers().isEmpty(),
                "functions=" + functionCount + " commands="
                        + script.handle().commands().size() + " triggers="
                        + script.handle().triggers().size()));

        // Every function must expose a resolvable JVM descriptor.
        checks.add(new Check("function signatures",
                script.handle().functions().values().stream()
                        .allMatch(spec -> spec.descriptor().startsWith("(")),
                "count=" + script.handle().functions().size()));
    }

    private static void describeCompilation(Loaded script) {
        Class<?> compiled = script.handle().compiledClass();
        System.out.printf("script         : %s%n", script.handle().id());
        System.out.printf("generated class: %s%n", compiled.getName());
        System.out.printf("functions      : %s%n", String.join(", ",
                new java.util.TreeSet<>(script.handle().functions().keySet())));
        int methods = 0;
        for (java.lang.reflect.Method method : compiled.getDeclaredMethods()) {
            methods++;
            System.out.printf("  %-24s %s%n", method.getName(), Signatures.describe(method));
        }
        System.out.printf("methods        : %d%n", methods);
    }

    /**
     * Compares JIT-compiled handlers with a tree-walking interpreter running the same script.
     *
     * <p>The interpreter walks the identical AST, so the numbers isolate the effect of
     * compilation rather than the effect of a hand-written Java loop.</p>
     */
    private static void benchmark(Loaded script, int iterations) {
        ScriptEngine engine = new ScriptEngine(new RecordingHost());
        ExecContext context = new ExecContext(new RecordingHost(), null, null, new Object[0],
                script.handle().id(), "bench");
        Interpreter interpreter = new Interpreter(script.parsed(), new RecordingHost());
        Interpreter.InterpreterContext interpretedContext = interpreter.newContext();

        int fibIterations = Math.max(500, iterations / 20);
        int sumIterations = Math.max(200, iterations / 10);

        long bytecodeFib = measure(fibIterations, () ->
                engine.callFunction(script, "fib", context, 20.0));
        long interpretedFib = measure(fibIterations, () ->
                interpreter.call("fib", interpretedContext, 20.0));
        long bytecodeSum = measure(sumIterations, () ->
                engine.callFunction(script, "sum-range", context, 1000.0));
        long interpretedSum = measure(sumIterations, () ->
                interpreter.call("sum-range", interpretedContext, 1000.0));

        System.out.printf("%-38s %12d ns/op%n", "fib(20) compiled bytecode", bytecodeFib);
        System.out.printf("%-38s %12d ns/op%n", "fib(20) tree-walking interpreter", interpretedFib);
        System.out.printf("%-38s %12d ns/op%n", "sum-range(1000) compiled bytecode", bytecodeSum);
        System.out.printf("%-38s %12d ns/op%n", "sum-range(1000) interpreter", interpretedSum);
        System.out.println();
        System.out.printf("speed-up vs interpreter: fib %.1fx, sum-range %.1fx%n",
                interpretedFib / (double) Math.max(1, bytecodeFib),
                interpretedSum / (double) Math.max(1, bytecodeSum));
    }

    private static long measure(int iterations, Runnable body) {
        // Warm up so the JIT has compiled the hot path before measuring.
        int warmup = Math.max(200, iterations / 5);
        for (int i = 0; i < warmup; i++) {
            body.run();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            body.run();
        }
        long elapsed = System.nanoTime() - start;
        return elapsed / Math.max(1, iterations);
    }

    /** Formats generated method signatures for the report. */
    static final class Signatures {

        static String describe(java.lang.reflect.Method method) {
            StringBuilder builder = new StringBuilder("(");
            Class<?>[] parameters = method.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(parameters[i].getSimpleName());
            }
            return builder.append(") -> ").append(method.getReturnType().getSimpleName()).toString();
        }
    }
}
