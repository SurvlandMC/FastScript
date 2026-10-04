package io.github.dsh.fastscript.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Storage for script variables.
 *
 * <p>Two namespaces exist on purpose:</p>
 * <ul>
 *   <li><b>global</b> — {@code $name}, visible to every script and optionally persisted to
 *       {@code variables.yml} across restarts.</li>
 *   <li><b>player</b> — {@code #name}, scoped to a player UUID, used for per-player state
 *       such as cooldowns or balances.</li>
 * </ul>
 *
 * <p>Lookups avoid boxing on the hot path by caching primitive copies of numeric globals;
 * the cache is only trusted while the mutation counter is unchanged, which keeps direct
 * writes through {@link #set(String, Object)} correct.</p>
 */
public final class VariableStore {

    private final Map<String, Object> globals = new ConcurrentHashMap<>();
    private final Map<String, Object> runtime = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> perPlayer = new ConcurrentHashMap<>();
    private final AtomicLong mutations = new AtomicLong();
    private final Object persistLock = new Object();

    public long mutations() {
        return mutations.get();
    }

    // ------------------------------------------------------------------ globals

    public Object get(String name) {
        return globals.get(name);
    }

    public Object getOrDefault(String name, Object fallback) {
        Object value = globals.get(name);
        return value == null ? fallback : value;
    }

    public void set(String name, Object value) {
        if (value == null) {
            globals.remove(name);
        } else {
            globals.put(name, value);
        }
        mutations.incrementAndGet();
    }

    public void add(String name, double delta) {
        globals.merge(name, delta, (current, added) -> {
            double result = toDouble(current) + (Double) added;
            return Math.floor(result) == result ? (Object) (long) result : (Object) result;
        });
        mutations.incrementAndGet();
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    public boolean has(String name) {
        return globals.containsKey(name);
    }

    public void remove(String name) {
        if (globals.remove(name) != null) {
            mutations.incrementAndGet();
        }
    }

    /** Names that exist in the runtime namespace (not persisted). */
    public Object getRuntime(String name) {
        return runtime.get(name);
    }

    public void setRuntime(String name, Object value) {
        if (value == null) {
            runtime.remove(name);
        } else {
            runtime.put(name, value);
        }
    }

    // ------------------------------------------------------------------ player scope

    public Object getPlayer(UUID player, String name) {
        if (player == null) {
            return null;
        }
        Map<String, Object> vars = perPlayer.get(player);
        return vars == null ? null : vars.get(name);
    }

    public void setPlayer(UUID player, String name, Object value) {
        if (player == null) {
            return;
        }
        Map<String, Object> vars = perPlayer.computeIfAbsent(player, key -> new ConcurrentHashMap<>());
        if (value == null) {
            vars.remove(name);
        } else {
            vars.put(name, value);
        }
        mutations.incrementAndGet();
    }

    public void addPlayer(UUID player, String name, double delta) {
        if (player == null) {
            return;
        }
        Map<String, Object> vars = perPlayer.computeIfAbsent(player, key -> new ConcurrentHashMap<>());
        vars.merge(name, delta, (current, added) -> toDouble(current) + (Double) added);
        mutations.incrementAndGet();
    }

    // ------------------------------------------------------------------ persistence

    /** Snapshot suitable for {@code variables.yml}; player scope is keyed by UUID text. */
    public Map<String, Object> snapshotGlobals() {
        return new LinkedHashMap<>(globals);
    }

    public Map<String, Object> snapshotPlayers() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        perPlayer.forEach((uuid, vars) -> snapshot.put(uuid.toString(), new LinkedHashMap<>(vars)));
        return snapshot;
    }

    public void restoreGlobals(Map<String, Object> values) {
        synchronized (persistLock) {
            globals.putAll(values);
            mutations.incrementAndGet();
        }
    }

    public void restorePlayers(Map<String, Object> values) {
        synchronized (persistLock) {
            values.forEach((key, value) -> {
                try {
                    UUID uuid = UUID.fromString(key);
                    if (value instanceof Map<?, ?> map) {
                        Map<String, Object> vars = new ConcurrentHashMap<>();
                        map.forEach((name, entry) -> vars.put(String.valueOf(name), entry));
                        perPlayer.put(uuid, vars);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed UUID keys instead of failing the whole load.
                }
            });
            mutations.incrementAndGet();
        }
    }

    public Map<String, Object> unmodifiableGlobals() {
        return Collections.unmodifiableMap(globals);
    }

    public void clear() {
        globals.clear();
        runtime.clear();
        perPlayer.clear();
        mutations.incrementAndGet();
    }
}
