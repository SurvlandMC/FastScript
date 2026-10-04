# Players and variables

## Where the player comes from

- In triggers/commands, receiver-less effects (`message`, `heal`, `give` …)
  act on the **event player** automatically.
- `sender()` is the sender object (the event player in triggers),
  `sender-name()` is their name as text.
- `player(name)` resolves an online player by nickname to an object;
  `null` when offline:

```fs
let victim = player(target)
if victim is null:
    message "Player '" + target + "' is not online."
    stop
message victim.name + " has " + victim.health + " hp"
```

## Player properties (read through the dot)

`name`, `display-name`, `health`, `max-health`, `food`, `level`, `exp`,
`x`, `y`, `z`, `yaw`, `pitch`, `world`, `gamemode`, `uuid`,
`is-op`, `is-flying`, `is-sneaking`, `is-sprinting`, `is-online`,
`ping`, `ip`.

Properties are **read-only**: assigning through a dot
(`victim.health = 20`) is rejected by the parser with
`cannot assign to this expression`. To change health there are
dedicated tools:

```fs
set-health(victim, 20)   # health of a specific player
heal 5                   # heal the event player by 5
```

An unknown property is `EvalException: unknown player property '...'`.

## Variables: `$`, `#`, persistence

| Form | Visibility | Storage |
|---|---|---|
| `$name` | All scripts | `variables.yml`, survives restarts |
| `#name` | One player (by UUID) | Memory (snapshot available, not written to yml) |
| `name` | Call-local | JVM local, the fastest |

```fs
$server-started = 0

on server start:
    $server-started = 1

on player join:
    #visits = #visits + 1
    message "Visit #" + #visits
```

Rules:

- Globals are saved to `plugins/FastScript/variables.yml`
  **when the plugin is disabled**, not on every change.
- Writing `#var` with no player in context is an error
  (`no player in context for a player-scoped variable`); reading is `null`.
- `null + 1` arithmetic: `Values.toNumber(null)` throws —
  initialize counters (`#visits = 0` somewhere up front, or guard with a check).
