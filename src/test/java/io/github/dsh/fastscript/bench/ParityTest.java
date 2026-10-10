package io.github.dsh.fastscript.bench;

import io.github.dsh.fastscript.ast.Ast;
import io.github.dsh.fastscript.engine.ScriptEngine;
import io.github.dsh.fastscript.lang.Parser;
import io.github.dsh.fastscript.runtime.ExecContext;
import io.github.dsh.fastscript.runtime.Host;
import io.github.dsh.fastscript.runtime.ScriptSource;
import io.github.dsh.fastscript.runtime.VariableStore;
import io.github.dsh.fastscript.tests.Suite;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Compiled bytecode must agree with the tree-walking interpreter on pure
 * functions. Same package as {@link Interpreter} for access to it.
 */
public final class ParityTest {

    private ParityTest() {
    }

    static final class Quiet implements Host {
        final VariableStore store = new VariableStore();

        @Override
        public void message(Player player, String text) {
        }

        @Override
        public void broadcast(String text) {
        }

        @Override
        public void log(String text) {
        }

        @Override
        public void damage(Player player, double amount) {
        }

        @Override
        public void heal(Player player, double amount) {
        }

        @Override
        public void kill(Player player) {
        }

        @Override
        public void teleport(Player player, double x, double y, double z) {
        }

        @Override
        public void giveItem(Player player, String material, int amount) {
        }

        @Override
        public void playSound(Player player, String sound) {
        }

        @Override
        public void sendTitle(Player player, String title, String subtitle) {
        }

        @Override
        public void kick(Player player, String reason) {
        }

        @Override
        public void setBlock(String world, int x, int y, int z, String material) {
        }

        @Override
        public void runLater(Runnable task, long delayTicks) {
            task.run();
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

    static final String SOURCE = ""
            + "function fib(n) : number:\n"
            + "    if n < 2:\n"
            + "        return n\n"
            + "    let a = 0\n"
            + "    let b = 1\n"
            + "    let i = 2\n"
            + "    while i <= n:\n"
            + "        let next = a + b\n"
            + "        a = b\n"
            + "        b = next\n"
            + "        i = i + 1\n"
            + "    return b\n"
            + "function echo-number(value) : number:\n"
            + "    return value\n"
            + "function sum-count(limit) : number:\n"
            + "    let iterations = 0\n"
            + "    loop limit times:\n"
            + "        iterations = iterations + 1\n"
            + "    return iterations\n"
            + "function sum-range(limit) : number:\n"
            + "    let total = 0\n"
            + "    loop limit times:\n"
            + "        total = total + loop-index\n"
            + "    return total\n"
            + "function countdown(from) : text:\n"
            + "    let result = \"\"\n"
            + "    let i = from\n"
            + "    while i > 0:\n"
            + "        result = result + i + \",\"\n"
            + "        i = i - 1\n"
            + "    return result\n";

    public static void run(Suite s) {
        Quiet host = new Quiet();
        ScriptEngine engine = new ScriptEngine(host);
        ScriptEngine.LoadResult result =
                engine.load(List.of(new ScriptSource("arith", "arith.fs", SOURCE, "test")));
        s.check(result.successful(), "parity.load " + result.errors());
        if (!result.successful()) {
            return;
        }
        var loaded = engine.scripts().values().iterator().next();
        Ast.Script parsed = Parser.parse(SOURCE, "arith.fs");
        Interpreter interpreter = new Interpreter(parsed, host);

        Object[][] cases = {
                {"fib", new Object[] {0.0}, 0.0},
                {"fib", new Object[] {1.0}, 1.0},
                {"fib", new Object[] {10.0}, 55.0},
                {"fib", new Object[] {20.0}, 6765.0},
                {"echo-number", new Object[] {7.5}, 7.5},
                {"sum-count", new Object[] {0.0}, 0.0},
                {"sum-count", new Object[] {5.0}, 5.0},
                {"sum-range", new Object[] {0.0}, 0.0},
                {"sum-range", new Object[] {10.0}, 45.0},
                {"sum-range", new Object[] {100.0}, 4950.0},
                {"countdown", new Object[] {0.0}, ""},
                {"countdown", new Object[] {1.0}, "1,"},
                {"countdown", new Object[] {3.0}, "3,2,1,"},
        };
        for (Object[] kase : cases) {
            String name = (String) kase[0];
            Object[] args = (Object[]) kase[1];
            Object expected = kase[2];
            ExecContext context = new ExecContext(host, null, null, new Object[0], "arith", name);
            Object compiled = engine.callFunction(loaded, name, context, args);
            Object interpreted = interpreter.call(name, interpreter.newContext(), args);
            s.eq(expected, compiled, "parity.compiled-" + name + args[0]);
            s.eq(expected, interpreted, "parity.interpreted-" + name + args[0]);
        }
    }
}
