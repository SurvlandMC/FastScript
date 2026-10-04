package io.github.dsh.fastscript.plugin;

import io.github.dsh.fastscript.core.Values;
import io.github.dsh.fastscript.engine.ScriptEngine;
import io.github.dsh.fastscript.runtime.CommandSpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FastScript: a compiled scripting engine for Paper and its forks.
 *
 * <p>Scripts are parsed once, compiled to JVM bytecode and registered as listeners and command
 * executors. Reloading recompiles in place: listeners for the previous script set are removed
 * first, so a reload never leaves stale handlers behind.</p>
 */
public final class FastScriptPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final String SCRIPT_DIRECTORY = "scripts";
    private static final String VARIABLE_FILE = "variables.yml";
    /** YAML section holding per-player variables keyed by UUID text. */
    private static final String PLAYER_SECTION = "__players__";

    private ScriptEngine engine;
    private PluginHost host;
    private final List<String> boundCommands = new ArrayList<>();

    @Override
    public void onEnable() {
        host = new PluginHost(this);
        engine = new ScriptEngine(host);

        createDefaultScriptIfMissing();
        loadVariables();
        reload();
        // reload() already (re)registers this listener; registering twice would
        // run every event handler twofold after startup.
        // Parameterless triggers such as `on server start` have no Bukkit event behind
        // them, so fire them once here. A later `/fastscript reload` intentionally does
        // not re-fire: reload recompiles, it does not restart the server.
        engine.fire("server start");

        PluginCommand root = getCommand("fastscript");
        if (root != null) {
            root.setExecutor(this);
            root.setTabCompleter(this);
        }
        getLogger().info("FastScript enabled: " + engine.scripts().size() + " script(s), "
                + engine.triggerCount() + " trigger(s), " + engine.commandNames().size() + " script command(s)");
        for (String error : engine.errors()) {
            getLogger().warning(error);
        }
    }

    @Override
    public void onDisable() {
        saveVariables();
        HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
    }

    // ------------------------------------------------------------------ loading

    /** Compiles every script and (re)binds listeners and script commands. */
    public ScriptEngine.LoadResult reload() {
        unbindCommands();
        HandlerList.unregisterAll((Listener) this);
        Bukkit.getPluginManager().registerEvents(this, this);

        ScriptEngine.LoadResult result = engine.loadDirectory(scriptDirectory());
        bindCommands();
        for (var entry : engine.scripts().entrySet()) {
            for (String trigger : entry.getValue().handle().triggers().keySet()) {
                String normalised = ScriptEvents.normalise(trigger);
                if (!normalised.equals("server start")
                        && !ScriptEvents.supportedNames().contains(normalised)) {
                    getLogger().warning("script '" + entry.getKey() + "' uses unknown event '"
                            + trigger + "'; it will never fire");
                }
            }
        }
        return result;
    }

    private Path scriptDirectory() {
        return Path.of(getDataFolder().getAbsolutePath(), SCRIPT_DIRECTORY);
    }

    /** Writes a small example script so a fresh install has something to run. */
    private void createDefaultScriptIfMissing() {
        Path directory = scriptDirectory();
        if (Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory);
            Path example = directory.resolve("example.fs");
            Files.writeString(example, """
                    # FastScript example. Edit or delete this file freely.

                    command heal(target : text) permission fastscript.heal:
                        message "Healing " + target

                    on player join:
                        message "Welcome to the server!"
                    """, StandardCharsets.UTF_8);
        } catch (IOException error) {
            getLogger().warning("cannot create the example script: " + error.getMessage());
        }
    }

    // ------------------------------------------------------------------ command binding

    private void bindCommands() {
        for (CommandSpec spec : engine.commandSpecs()) {
            PluginCommand command = getCommand(spec.name());
            if (command == null) {
                getLogger().warning("script command '/" + spec.name()
                        + "' is not declared in plugin.yml; add it there to make it available");
                continue;
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
            if (spec.permission() != null && !spec.permission().isBlank()) {
                command.setPermission(spec.permission());
            }
            boundCommands.add(spec.name());
        }
    }

    private void unbindCommands() {
        for (String name : boundCommands) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        }
        boundCommands.clear();
    }

    // ------------------------------------------------------------------ events

    /**
     * Forwards one Bukkit event to every script handler interested in it.
     *
     * <p>Keep events use {@code ignoreCancelled = true} so a cancelled interaction never runs
     * script logic; the check is done by Bukkit rather than by generated code.</p>
     */
    private void dispatch(String eventName, Event event) {
        if (engine == null) {
            return;
        }
        var player = ScriptEvents.playerFor(event);
        // Cancellation is applied to this event object by the engine itself.
        engine.dispatch(eventName, player, event);
    }

    /** True when at least one script asked for this event, so the listener body can bail early. */
    private boolean wants(String eventName) {
        return engine != null && engine.eventNames().contains(ScriptEvents.normalise(eventName));
    }

    /**
     * Explicit listeners rather than dynamic registration.
     *
     * <p>Bukkit's dynamic {@code registerEvent} takes the event class reflectively, but plain
     * {@code @EventHandler} methods are compatible with every server and version in use, so the
     * engine pays only a map lookup when no script is interested.</p>
     */
    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        dispatch("join", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        dispatch("quit", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        dispatch("death", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        dispatch("respawn", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        dispatch("chat", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        dispatch("command", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        dispatch("interact", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        dispatch("move", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        dispatch("break block", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        dispatch("place block", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        dispatch("drop item", event);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        dispatch("damage", event);
    }

    // ------------------------------------------------------------------ admin command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("fastscript")) {
            return handleAdmin(sender, args);
        }
        // A script command: argument zero is the sender, the rest are positional values.
        if (engine == null) {
            return true;
        }
        String cmdName = command.getName().toLowerCase(Locale.ROOT);
        if (!commandAllowed(engine.commandSpecs(), cmdName, sender::hasPermission)) {
            reply(sender, "You don't have permission to use /" + command.getName() + ".");
            return true;
        }
        Object[] forwarded = new Object[args.length + 1];
        forwarded[0] = sender;
        System.arraycopy(args, 0, forwarded, 1, args.length);
        engine.dispatchCommand(cmdName, forwarded);
        return true;
    }

    /**
     * Whether a sender may run a script command. When several scripts declare the
     * same label, one open (or permitted) declaration is enough to allow it; only
     * when every declaration restricts access and none matches is the sender denied.
     */
    public static boolean commandAllowed(List<CommandSpec> specs, String label,
            java.util.function.Predicate<String> hasPermission) {
        boolean restricted = false;
        for (CommandSpec spec : specs) {
            if (!spec.name().equalsIgnoreCase(label)) {
                continue;
            }
            if (spec.permission() == null || spec.permission().isBlank()
                    || hasPermission.test(spec.permission())) {
                return true;
            }
            restricted = true;
        }
        return !restricted;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fastscript.admin")) {
            reply(sender, "You do not have permission to use FastScript.");
            return true;
        }
        String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload" -> {
                ScriptEngine.LoadResult result = reload();
                reply(sender, "FastScript reloaded: " + result.loaded() + "/" + result.total()
                        + " script(s) in " + System.lineSeparator());
                for (String error : result.errors()) {
                    reply(sender, "  " + error);
                }
            }
            case "scripts" -> {
                for (var entry : engine.scripts().entrySet()) {
                    var handle = entry.getValue().handle();
                    reply(sender, entry.getKey() + ": " + handle.triggers().size() + " trigger(s), "
                            + handle.commands().size() + " command(s), "
                            + handle.functions().size() + " function(s)");
                }
            }
            case "variables" -> reply(sender, "Persistent variables: "
                    + host.variables().snapshotGlobals().size());
            case "bench" -> runBenchmark(sender, args);
            default -> reply(sender, """
                    FastScript commands:
                      /fastscript reload    - recompile every script
                      /fastscript scripts   - list loaded scripts
                      /fastscript variables - variable count
                      /fastscript bench     - measure dispatch cost""");
        }
        return true;
    }

    /**
     * Measures script dispatch cost so operators can compare against a reference engine.
     *
     * <p>The benchmark runs the loaded script functions directly, which isolates compilation
     * quality from event plumbing.</p>
     */
    private void runBenchmark(CommandSender sender, String[] args) {
        int iterations = 20_000;
        if (args.length > 1) {
            try {
                iterations = Math.max(1000, Integer.parseInt(args[1]));
            } catch (NumberFormatException error) {
                reply(sender, "usage: /fastscript bench [iterations]");
                return;
            }
        }
        var scripts = engine.scripts();
        if (scripts.isEmpty()) {
            reply(sender, "No scripts loaded.");
            return;
        }
        var entry = scripts.values().iterator().next();
        var spec = entry.handle().functions().values().stream().findFirst().orElse(null);
        if (spec == null) {
            reply(sender, "The first script declares no functions to benchmark.");
            return;
        }
        var context = new io.github.dsh.fastscript.runtime.ExecContext(host, null, null,
                new Object[0], entry.handle().id(), "bench");
        Object[] arguments = new Object[spec.arity()];
        java.util.Arrays.fill(arguments, 10.0);
        // Warm up, then measure.
        for (int index = 0; index < 2000; index++) {
            engine.callFunction(entry, spec.name(), context, arguments);
        }
        long start = System.nanoTime();
        for (int index = 0; index < iterations; index++) {
            engine.callFunction(entry, spec.name(), context, arguments);
        }
        long elapsed = System.nanoTime() - start;
        double nanosPerCall = elapsed / (double) iterations;
        double callsPerSecond = 1_000_000_000.0 / Math.max(1.0, nanosPerCall);
        reply(sender, String.format(Locale.ROOT,
                "%s.%s: %.0f ns/call, %.0f calls/s over %d iterations",
                entry.handle().id(), spec.name(), nanosPerCall, callsPerSecond, iterations));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("fastscript") && args.length == 1) {
            return List.of("reload", "scripts", "variables", "bench").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    // ------------------------------------------------------------------ variables

    private File variableFile() {
        return new File(getDataFolder(), VARIABLE_FILE);
    }

    private void loadVariables() {
        File file = variableFile();
        if (!file.isFile()) {
            return;
        }
        try (var reader = new java.io.InputStreamReader(Files.newInputStream(file.toPath()),
                StandardCharsets.UTF_8)) {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
            var globals = new java.util.LinkedHashMap<String, Object>();
            for (String key : yaml.getKeys(false)) {
                if (key.equals(PLAYER_SECTION)) {
                    continue;
                }
                Object value = yaml.get(key);
                if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
                    globals.put(key, new java.util.LinkedHashMap<>(section.getValues(false)));
                } else {
                    globals.put(key, value);
                }
            }
            host.variables().restoreGlobals(globals);
            Object players = yaml.get(PLAYER_SECTION);
            if (players instanceof java.util.Map<?, ?> map) {
                var restored = new java.util.LinkedHashMap<String, Object>();
                map.forEach((key, value) -> restored.put(String.valueOf(key), value));
                host.variables().restorePlayers(restored);
            }
        } catch (IOException error) {
            getLogger().warning("cannot read variables: " + error.getMessage());
        }
    }

    private void saveVariables() {
        if (host == null) {
            return;
        }
        try {
            getDataFolder().mkdirs();
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    new org.bukkit.configuration.file.YamlConfiguration();
            host.variables().snapshotGlobals().forEach(yaml::set);
            yaml.set(PLAYER_SECTION, host.variables().snapshotPlayers());
            yaml.save(variableFile());
        } catch (IOException error) {
            getLogger().warning("cannot save variables: " + error.getMessage());
        }
    }

    /** Exposed for the API and for tests; harmless if unused by scripts. */
    public ScriptEngine engine() {
        return engine;
    }

    /** Formats a value the way scripts see it. */
    public static String describe(Object value) {
        return Values.toText(value);
    }

    /**
     * Sends a plain text reply.
     *
     * <p>Adventure components are used instead of raw strings: the legacy {@code String} overload
     * of {@code sendMessage} pulls in the deprecated Bungee chat library, which the plugin does
     * not depend on.</p>
     */
    private static void reply(CommandSender sender, String message) {
        sender.sendMessage(message);
    }
}
