package io.github.dsh.fastscript.runtime;

import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Everything generated script code needs from the server, expressed without any Bukkit
 * dependency so scripts can run — and be benchmarked — outside a live server.
 */
public interface Host {

    void message(Player player, String text);

    void broadcast(String text);

    void log(String text);

    void damage(Player player, double amount);

    void heal(Player player, double amount);

    void kill(Player player);

    void teleport(Player player, double x, double y, double z);

    void giveItem(Player player, String material, int amount);

    void playSound(Player player, String sound);

    void sendTitle(Player player, String title, String subtitle);

    void kick(Player player, String reason);

    void setBlock(String world, int x, int y, int z, String material);

    void runLater(Runnable task, long delayTicks);

    void cancelEvent();

    /** Persisted variables the host owns; the plugin implementation exposes them in-game. */
    VariableStore variables();

    Map<String, Object> scriptInfo();
}
