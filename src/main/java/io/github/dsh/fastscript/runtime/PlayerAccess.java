package io.github.dsh.fastscript.runtime;

import io.github.dsh.fastscript.core.EvalException;
import io.github.dsh.fastscript.core.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Reflection-free access to the handful of {@code Player} properties scripts can read.
 *
 * <p>All calls land on the stable Bukkit interfaces rather than server internals, so the
 * same code works on Paper and on forks such as Leaf.</p>
 */
public final class PlayerAccess {

    private PlayerAccess() {
    }

    public static Player require(Object value) {
        if (value instanceof Player player) {
            return player;
        }
        throw new EvalException("expected a player, got " + Values.typeName(value));
    }

    public static Object get(Object receiver, String property) {
        if (receiver instanceof Player player) {
            return fromPlayer(player, property);
        }
        if (receiver instanceof List<?> list && property.equals("size")) {
            return list.size();
        }
        throw new EvalException("unknown property '" + property + "' on " + Values.typeName(receiver));
    }

    private static Object fromPlayer(Player player, String property) {
        return switch (property) {
            case "name" -> player.getName();
            case "display-name" -> player.getDisplayName();
            case "health" -> player.getHealth();
            case "max-health" -> player.getMaxHealth();
            case "food" -> player.getFoodLevel();
            case "level" -> player.getLevel();
            case "exp" -> (double) player.getExp();
            case "x" -> player.getLocation().getX();
            case "y" -> player.getLocation().getY();
            case "z" -> player.getLocation().getZ();
            case "yaw" -> (double) player.getLocation().getYaw();
            case "pitch" -> (double) player.getLocation().getPitch();
            case "world" -> player.getWorld().getName();
            case "gamemode" -> player.getGameMode().name();
            case "uuid" -> player.getUniqueId().toString();
            case "is-op" -> player.isOp();
            case "is-flying" -> player.isFlying();
            case "is-sneaking" -> player.isSneaking();
            case "is-sprinting" -> player.isSprinting();
            case "is-online" -> player.isOnline();
            case "ping" -> player.getPing();
            case "ip" -> player.getAddress() == null ? "" : player.getAddress().getHostString();
            default -> throw new EvalException("unknown player property '" + property + "'");
        };
    }

    public static void set(Object receiver, String property, Object value) {
        if (!(receiver instanceof Player player)) {
            throw new EvalException("cannot set '" + property + "' on " + Values.typeName(receiver));
        }
        switch (property) {
            case "health" -> player.setHealth(clamp(Values.toNumber(value), 0.0, player.getMaxHealth()));
            case "food" -> player.setFoodLevel((int) Values.toLong(value));
            case "level" -> player.setLevel((int) Values.toLong(value));
            case "exp" -> player.setExp((float) clamp(Values.toNumber(value), 0.0, 1.0));
            case "display-name" -> player.setDisplayName(Values.toText(value));
            case "flying" -> player.setFlying(Values.toBool(value));
            case "sneaking" -> player.setSneaking(Values.toBool(value));
            case "sprinting" -> player.setSprinting(Values.toBool(value));
            default -> throw new EvalException("player property '" + property + "' is read-only");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Resolves a player by exact name, used by commands such as {@code /heal <player>}. */
    public static Player byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        // Offline lookup by name requires the server to know the profile; UUID lookup is free.
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        Player resolved = offline == null ? null : offline.getPlayer();
        return resolved;
    }

    public static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            throw new EvalException("unknown material '" + name + "'");
        }
        return material;
    }

    public static ItemStack item(String name, int amount) {
        return new ItemStack(material(name), Math.max(1, amount));
    }

    public static World world(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new EvalException("unknown world '" + name + "'");
        }
        return world;
    }

    public static List<Object> onlinePlayers() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }

    public static UUID uuidOf(Object value) {
        if (value instanceof Player player) {
            return player.getUniqueId();
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException error) {
                Player player = byName(text);
                return player == null ? null : player.getUniqueId();
            }
        }
        return null;
    }
}
