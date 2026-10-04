package io.github.dsh.fastscript.plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Maps script event names to Bukkit event classes and extracts the acting player.
 *
 * <p>Names are matched after normalisation, so {@code on player join}, {@code on join} and
 * {@code on join event} all resolve to the same trigger. The mapping is deliberately explicit:
 * it keeps the generated code free of any Bukkit reference and makes the supported surface
 * obvious from one place.</p>
 */
final class ScriptEvents {

    private static final Map<String, Class<? extends Event>> EVENTS = new LinkedHashMap<>();

    static {
        register("join", PlayerJoinEvent.class);
        register("quit", PlayerQuitEvent.class);
        register("death", PlayerDeathEvent.class);
        register("respawn", PlayerRespawnEvent.class);
        register("chat", AsyncPlayerChatEvent.class);
        register("command", PlayerCommandPreprocessEvent.class);
        register("interact", PlayerInteractEvent.class);
        register("move", PlayerMoveEvent.class);
        register("break block", BlockBreakEvent.class);
        register("place block", BlockPlaceEvent.class);
        register("drop item", PlayerDropItemEvent.class);
        register("damage", EntityDamageEvent.class);
    }

    private ScriptEvents() {
    }

    private static void register(String name, Class<? extends Event> type) {
        EVENTS.put(name, type);
        EVENTS.put("player " + name, type);
        EVENTS.put(name + " event", type);
        EVENTS.put("player " + name + " event", type);
    }

    /** Normalises a script event header to the key used by {@link #lookup(String)}. */
    static String normalise(String raw) {
        String name = raw.toLowerCase(java.util.Locale.ROOT).trim();
        if (name.startsWith("on ")) {
            name = name.substring(3).trim();
        }
        return name.replaceAll("\\s+", " ");
    }

    static Class<? extends Event> classFor(String raw) {
        Class<? extends Event> type = EVENTS.get(normalise(raw));
        if (type == null) {
            throw new IllegalArgumentException("unsupported event '" + raw + "'");
        }
        return type;
    }

    /** @return the acting player for an event, or {@code null} when there is none */
    static Player playerFor(Event event) {
        if (event instanceof PlayerJoinEvent join) {
            return join.getPlayer();
        }
        if (event instanceof PlayerQuitEvent quit) {
            return quit.getPlayer();
        }
        if (event instanceof PlayerDeathEvent death) {
            return death.getEntity();
        }
        if (event instanceof PlayerRespawnEvent respawn) {
            return respawn.getPlayer();
        }
        if (event instanceof AsyncPlayerChatEvent chat) {
            return chat.getPlayer();
        }
        if (event instanceof PlayerCommandPreprocessEvent command) {
            return command.getPlayer();
        }
        if (event instanceof PlayerInteractEvent interact) {
            return interact.getPlayer();
        }
        if (event instanceof PlayerMoveEvent move) {
            return move.getPlayer();
        }
        if (event instanceof BlockBreakEvent broke) {
            return broke.getPlayer();
        }
        if (event instanceof BlockPlaceEvent placed) {
            return placed.getPlayer();
        }
        if (event instanceof PlayerDropItemEvent drop) {
            return drop.getPlayer();
        }
        if (event instanceof EntityDamageEvent damage && damage.getEntity() instanceof Player player) {
            return player;
        }
        return null;
    }

    /** Human readable list of supported event names, used by diagnostics. */
    static java.util.Set<String> supportedNames() {
        return java.util.Set.copyOf(EVENTS.keySet());
    }
}
