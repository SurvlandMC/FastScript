package io.github.dsh.fastscript.tests;

import io.github.dsh.fastscript.core.EvalException;
import io.github.dsh.fastscript.engine.ScriptEngine;
import io.github.dsh.fastscript.engine.ScriptLoader;
import io.github.dsh.fastscript.runtime.ExecContext;
import io.github.dsh.fastscript.runtime.Host;
import io.github.dsh.fastscript.runtime.ScriptSource;
import io.github.dsh.fastscript.runtime.VariableStore;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * End-to-end engine tests with a recording host and proxy players: no server.
 * Covers specific builtin calls, loops, logic, user methods, variables,
 * commands and both load-time and runtime failures.
 */
public final class ScriptTest {

    private ScriptTest() {
    }

    /** Host stub that records every effect instead of touching a server. */
    static final class Stub implements Host {
        final List<String> messages = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final List<String> broadcasts = new ArrayList<>();
        final List<String> gives = new ArrayList<>();
        final List<String> effects = new ArrayList<>();
        final VariableStore store = new VariableStore();

        static String name(Player player) {
            return player == null ? "<none>" : player.getName();
        }

        @Override
        public void message(Player player, String text) {
            messages.add(name(player) + ":" + text);
        }

        @Override
        public void broadcast(String text) {
            broadcasts.add(text);
        }

        @Override
        public void log(String text) {
            logs.add(text);
        }

        @Override
        public void damage(Player player, double amount) {
            effects.add("damage:" + amount);
        }

        @Override
        public void heal(Player player, double amount) {
            effects.add("heal:" + amount);
        }

        @Override
        public void kill(Player player) {
            effects.add("kill:" + name(player));
        }

        @Override
        public void teleport(Player player, double x, double y, double z) {
            effects.add("teleport:" + x + "," + y + "," + z);
        }

        @Override
        public void giveItem(Player player, String material, int amount) {
            gives.add(material + "x" + amount);
        }

        @Override
        public void playSound(Player player, String sound) {
            effects.add("sound:" + sound);
        }

        @Override
        public void sendTitle(Player player, String title, String subtitle) {
            effects.add("title:" + title + "/" + subtitle);
        }

        @Override
        public void kick(Player player, String reason) {
            effects.add("kick:" + reason);
        }

        @Override
        public void setBlock(String world, int x, int y, int z, String material) {
            effects.add("block:" + world + ":" + x + "," + y + "," + z + ":" + material);
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

    /** Player proxy with a fixed name/uuid/health; set-calls are recorded. */
    static Player player(String name, UUID uuid, List<Double> healthLog) {
        double[] health = {20.0};
        return (Player) Proxy.newProxyInstance(ScriptTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName", "getDisplayName" -> name;
                    case "getUniqueId" -> uuid;
                    case "getHealth" -> health[0];
                    case "getMaxHealth" -> 20.0;
                    case "setHealth" -> {
                        health[0] = (double) args[0];
                        healthLog.add(health[0]);
                        yield null;
                    }
                    case "isOp", "isFlying", "isSneaking", "isSprinting", "isOnline" -> false;
                    case "getFoodLevel", "getLevel", "getPing" -> 0;
                    case "getExp" -> 0.0f;
                    case "toString" -> "Player(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> {
                        Class<?> returns = method.getReturnType();
                        if (returns == boolean.class) {
                            yield false;
                        }
                        if (returns == int.class) {
                            yield 0;
                        }
                        if (returns == long.class) {
                            yield 0L;
                        }
                        if (returns == double.class) {
                            yield 0.0;
                        }
                        if (returns == float.class) {
                            yield 0.0f;
                        }
                        yield null;
                    }
                });
    }

    static Player player(String name) {
        return player(name, UUID.randomUUID(), new ArrayList<>());
    }

    static Cancellable cancellable(List<Boolean> log) {
        return (Cancellable) Proxy.newProxyInstance(ScriptTest.class.getClassLoader(),
                new Class<?>[] {Cancellable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setCancelled" -> {
                        log.add((Boolean) args[0]);
                        yield null;
                    }
                    case "isCancelled" -> false;
                    default -> null;
                });
    }

    static ScriptEngine load(Stub stub, Suite s, String id, String source) {
        ScriptEngine engine = new ScriptEngine(stub);
        ScriptEngine.LoadResult result =
                engine.load(List.of(new ScriptSource(id, id + ".fs", source, "test")));
        s.check(result.successful(), "load " + id + " " + result.errors());
        return engine;
    }

    static Object call(ScriptEngine engine, String id, String function, Object... args) {
        ScriptLoader.Loaded loaded = engine.scripts().values().iterator().next();
        ExecContext context = new ExecContext(engine.host(), null, null, new Object[0], id, function);
        return engine.callFunction(loaded, function, context, args);
    }

    public static void run(Suite s) {
        // --- specific builtin calls ----------------------------------------
        Stub stub = new Stub();
        Player steve = player("Steve");
        ScriptEngine engine = load(stub, s, "calls",
                "on join:\n"
                        + "    message \"hi \" + sender-name()\n"
                        + "    log \"joined\"\n"
                        + "    broadcast \"welcome\"\n"
                        + "    give \"DIAMOND\" 3\n"
                        + "    teleport 1 2 3\n"
                        + "    kill\n"
                        + "    heal 5\n"
                        + "    damage 2\n"
                        + "    sound \"pop\"\n"
                        + "    title \"A\" \"B\"\n"
                        + "    kick \"out\"\n"
                        + "    set-block \"world\" 1 2 3 \"STONE\"\n");
        engine.dispatch("join", steve, null);
        s.eq(List.of("Steve:hi Steve"), stub.messages, "engine.message-player");
        s.eq(List.of("joined"), stub.logs, "engine.log");
        s.eq(List.of("welcome"), stub.broadcasts, "engine.broadcast");
        s.eq(List.of("DIAMONDx3"), stub.gives, "engine.give");
        s.check(stub.effects.containsAll(List.of("teleport:1.0,2.0,3.0", "kill:Steve",
                "heal:5.0", "damage:2.0", "sound:pop", "title:A/B", "kick:out",
                "block:world:1,2,3:STONE")), "engine.player-effects " + stub.effects);
        s.check(engine.errors().isEmpty(), "engine.no-errors");

        // --- stop halts the handler -----------------------------------------
        Stub stub2 = new Stub();
        ScriptEngine engine2 = load(stub2, s, "stop",
                "on join:\n    message \"first\"\n    stop\n    message \"second\"\n");
        engine2.dispatch("join", player("A"), null);
        s.eq(1, stub2.messages.size(), "engine.stop-halts");

        // --- cancel-event reaches a cancellable event ------------------------
        Stub stub3 = new Stub();
        ScriptEngine engine3 = load(stub3, s, "cancel",
                "on break:\n    cancel-event\n");
        List<Boolean> cancelled = new ArrayList<>();
        engine3.dispatch("break", player("B"), cancellable(cancelled));
        s.eq(List.of(true), cancelled, "engine.cancel-event");

        // --- commands ---------------------------------------------------------
        Stub stub4 = new Stub();
        Player bob = player("Bob");
        ScriptEngine engine4 = load(stub4, s, "cmds",
                "command greet(name : text):\n    message \"hi \" + name\n");
        engine4.dispatchCommand("greet", new Object[] {bob, "Zed"});
        s.eq(List.of("Bob:hi Zed"), stub4.messages, "engine.command-args");
        s.check(engine4.commandNames().contains("greet"), "engine.command-registered");

        // --- loops ------------------------------------------------------------
        Stub stub5 = new Stub();
        ScriptEngine engine5 = load(stub5, s, "loops",
                "function triangle(n) : number:\n"
                        + "    let total = 0\n"
                        + "    loop n times:\n"
                        + "        total = total + loop-index\n"
                        + "    return total\n"
                        + "function countdown(from) : text:\n"
                        + "    let result = \"\"\n"
                        + "    let i = from\n"
                        + "    while i > 0:\n"
                        + "        result = result + i + \",\"\n"
                        + "        i = i - 1\n"
                        + "    return result\n"
                        + "function skip() : number:\n"
                        + "    let total = 0\n"
                        + "    loop 10 times:\n"
                        + "        if loop-index == 3:\n"
                        + "            continue\n"
                        + "        if loop-index == 7:\n"
                        + "            break\n"
                        + "        total = total + 1\n"
                        + "    return total\n"
                        + "on join:\n"
                        + "    for item in split(\"a,b,c\", \",\"):\n"
                        + "        log item\n");
        s.eq(10.0, call(engine5, "loops", "triangle", 5.0), "engine.loop-index-sum");
        s.eq("3,2,1,", call(engine5, "loops", "countdown", 3.0), "engine.while-countdown");
        s.eq(6.0, call(engine5, "loops", "skip"), "engine.break-continue");
        engine5.dispatch("join", player("D"), null);
        s.eq(List.of("a", "b", "c"), stub5.logs, "engine.for-each");

        // --- logic ------------------------------------------------------------
        Stub stub6 = new Stub();
        ScriptEngine engine6 = load(stub6, s, "logic",
                "function grade(score) : text:\n"
                        + "    if score >= 90:\n"
                        + "        return \"A\"\n"
                        + "    else if score >= 70:\n"
                        + "        return \"B\"\n"
                        + "    else:\n"
                        + "        return \"F\"\n"
                        + "function logic(a, b, c) : text:\n"
                        + "    if a and not b or c:\n"
                        + "        return \"yes\"\n"
                        + "    return \"no\"\n"
                        + "function sign(x) : text:\n"
                        + "    return x > 0 ? \"pos\" : \"neg\"\n"
                        + "function describe(x) : text:\n"
                        + "    return x is null ? \"none\" : \"some\"\n"
                        + "function cmp(a, b) : text:\n"
                        + "    let out = \"\"\n"
                        + "    if a < b:\n"
                        + "        out = out + \"<\"\n"
                        + "    if a > b:\n"
                        + "        out = out + \">\"\n"
                        + "    if a == b:\n"
                        + "        out = out + \"=\"\n"
                        + "    if a != b:\n"
                        + "        out = out + \"!\"\n"
                        + "    if a <= b:\n"
                        + "        out = out + \"l\"\n"
                        + "    if a >= b:\n"
                        + "        out = out + \"g\"\n"
                        + "    return out\n");
        s.eq("A", call(engine6, "logic", "grade", 95.0), "engine.if-elif");
        s.eq("B", call(engine6, "logic", "grade", 75.0), "engine.if-elif-2");
        s.eq("F", call(engine6, "logic", "grade", 10.0), "engine.else");
        s.eq("yes", call(engine6, "logic", "logic", true, false, false), "engine.and-not");
        s.eq("yes", call(engine6, "logic", "logic", false, true, true), "engine.or");
        s.eq("no", call(engine6, "logic", "logic", false, false, false), "engine.all-false");
        s.eq("pos", call(engine6, "logic", "sign", 3.0), "engine.ternary-pos");
        s.eq("neg", call(engine6, "logic", "sign", -1.0), "engine.ternary-neg");
        s.eq("none", call(engine6, "logic", "describe", (Object) null), "engine.is-null");
        s.eq("some", call(engine6, "logic", "describe", "x"), "engine.is-not-null");
        s.eq("<!l", call(engine6, "logic", "cmp", 1.0, 2.0), "engine.comparisons-lt");
        s.eq(">!g", call(engine6, "logic", "cmp", 2.0, 1.0), "engine.comparisons-gt");
        s.eq("=lg", call(engine6, "logic", "cmp", 2.0, 2.0), "engine.comparisons-eq");

        // --- user methods -----------------------------------------------------
        Stub stub7 = new Stub();
        ScriptEngine engine7 = load(stub7, s, "methods",
                "function double(x) : number:\n"
                        + "    return x * 2\n"
                        + "function quad(x) : number:\n"
                        + "    return double(double(x))\n"
                        + "function echo-number(value) : number:\n"
                        + "    return value\n");
        s.eq(12.0, call(engine7, "methods", "quad", 3.0), "engine.nested-calls");
        s.eq(7.5, call(engine7, "methods", "echo-number", 7.5), "engine.number-passthrough");

        // --- variables ----------------------------------------------------------
        Stub stub8 = new Stub();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ScriptEngine engine8 = load(stub8, s, "vars",
                "$counter = 0\n"
                        + "on join:\n"
                        + "    if #visits is null:\n"
                        + "        #visits = 0\n"
                        + "    #visits = #visits + 1\n"
                        + "    $counter = $counter + 1\n"
                        + "    message \"visit \" + #visits\n");
        Player p1 = player("One", u1, new ArrayList<>());
        Player p2 = player("Two", u2, new ArrayList<>());
        engine8.dispatch("join", p1, null);
        engine8.dispatch("join", p1, null);
        engine8.dispatch("join", p2, null);
        s.eq(List.of("One:visit 1", "One:visit 2", "Two:visit 1"), stub8.messages,
                "engine.player-isolation");
        s.eq(3.0, stub8.store.get("counter"), "engine.global-persists");
        s.eq(2.0, stub8.store.getPlayer(u1, "visits"), "engine.player-store");
        s.eq(null, stub8.store.getPlayer(UUID.randomUUID(), "visits"), "engine.player-unknown");

        // --- player properties --------------------------------------------------
        Stub stub9 = new Stub();
        List<Double> healthLog = new ArrayList<>();
        Player probe = player("Probe", UUID.randomUUID(), healthLog);
        ScriptEngine engine9 = load(stub9, s, "props",
                "on join:\n"
                        + "    let me = sender()\n"
                        + "    message me.name\n"
                        + "    me.health = 10\n");
        engine9.dispatch("join", probe, null);
        s.eq(List.of("Probe:Probe"), stub9.messages, "engine.property-read");
        s.eq(List.of(10.0), healthLog, "engine.property-write");

        // --- load-time failure ----------------------------------------------------
        Stub stub10 = new Stub();
        ScriptEngine engine10 = new ScriptEngine(stub10);
        ScriptEngine.LoadResult bad = engine10.load(List.of(
                new ScriptSource("bad", "bad.fs", "on join:\n    nosuchfn()\n", "test")));
        s.check(!bad.successful() && !bad.errors().isEmpty(), "engine.load-failure-reported");

        // --- runtime failure ------------------------------------------------------
        Stub stub11 = new Stub();
        ScriptEngine engine11 = load(stub11, s, "boom",
                "function boom(z) : number:\n    return 1 / z\n");
        s.failsMsg("division by zero",
                () -> call(engine11, "boom", "boom", 0.0), "engine.runtime-error-raises");
        Stub stub12 = new Stub();
        ScriptEngine engine12 = load(stub12, s, "boom2",
                "on join:\n    #z = 0\n    log 1 / #z\n");
        engine12.dispatch("join", player("C"), null);
        s.check(!engine12.errors().isEmpty(), "engine.runtime-error-recorded");
    }
}
