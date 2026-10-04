package io.github.dsh.fastscript.plugin;

import io.github.dsh.fastscript.runtime.Host;
import io.github.dsh.fastscript.runtime.VariableStore;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The server side of the engine: performs the effects scripts request.
 *
 * <p>Keeping this behind {@link Host} means the engine, the compiler and the benchmarks never
 * touch Bukkit directly, so compiled scripts can be tested without a running server.</p>
 */
public final class PluginHost implements Host {

    private final JavaPlugin plugin;
    private final VariableStore store = new VariableStore();
    private volatile boolean eventCancelled;

    public PluginHost(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Consumes the cancellation flag set by {@code cancel-event} during the last dispatch. */
    public boolean consumeCancelled() {
        boolean value = eventCancelled;
        eventCancelled = false;
        return value;
    }

    @Override
    public void message(Player player, String text) {
        if (player != null) {
            player.sendMessage(text);
        }
    }

    @Override
    public void broadcast(String text) {
        Bukkit.broadcastMessage(text);
    }

    @Override
    public void log(String text) {
        plugin.getLogger().info(text);
    }

    @Override
    public void damage(Player player, double amount) {
        if (player != null) {
            player.damage(amount);
        }
    }

    @Override
    public void heal(Player player, double amount) {
        if (player != null) {
            double target = Math.min(player.getMaxHealth(), player.getHealth() + amount);
            player.setHealth(Math.max(0.0, target));
        }
    }

    @Override
    public void kill(Player player) {
        if (player != null) {
            player.setHealth(0.0);
        }
    }

    @Override
    public void teleport(Player player, double x, double y, double z) {
        if (player != null) {
            player.teleport(new org.bukkit.Location(player.getWorld(), x, y, z));
        }
    }

    @Override
    public void giveItem(Player player, String material, int amount) {
        if (player == null) {
            return;
        }
        try {
            player.getInventory().addItem(io.github.dsh.fastscript.runtime.PlayerAccess.item(material, amount));
        } catch (RuntimeException error) {
            plugin.getLogger().warning("give: " + error.getMessage());
        }
    }

    @Override
    public void playSound(Player player, String sound) {
        if (player == null) {
            return;
        }
        // Sound names are resolved through the modern registry; the legacy Sound enum is on its
        // way out and would tie the plugin to a specific server version.
        org.bukkit.NamespacedKey key = sound.contains(":")
                ? org.bukkit.NamespacedKey.fromString(sound.toLowerCase(java.util.Locale.ROOT))
                : org.bukkit.NamespacedKey.minecraft(sound.toLowerCase(java.util.Locale.ROOT));
        if (key == null) {
            plugin.getLogger().warning("sound: malformed sound name '" + sound + "'");
            return;
        }
        org.bukkit.Sound resolved = org.bukkit.Registry.SOUNDS.get(key);
        if (resolved == null) {
            plugin.getLogger().warning("sound: unknown sound '" + sound + "'");
            return;
        }
        player.playSound(player.getLocation(), resolved, 1.0f, 1.0f);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void sendTitle(Player player, String title, String subtitle) {
        if (player != null) {
            player.sendTitle(title, subtitle, 10, 70, 20);
        }
    }

    @Override
    public void kick(Player player, String reason) {
        if (player != null) {
            player.kickPlayer(reason);
        }
    }

    @Override
    public void setBlock(String world, int x, int y, int z, String material) {
        World target = Bukkit.getWorld(world);
        if (target == null) {
            plugin.getLogger().warning("set-block: unknown world '" + world + "'");
            return;
        }
        target.getBlockAt(x, y, z).setType(
                io.github.dsh.fastscript.runtime.PlayerAccess.material(material));
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void cancelEvent() {
        eventCancelled = true;
    }

    @Override
    public VariableStore variables() {
        return store;
    }

    @Override
    public Map<String, Object> scriptInfo() {
        return Map.of("plugin", plugin.getName());
    }

}
