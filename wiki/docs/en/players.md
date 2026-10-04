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

Properties read through the dot, and the writable ones also assign:
`health`, `food`, `level`, `exp`, `display-name`, `flying`, `sneaking`,
`sprinting` (written straight through `PlayerAccess.set`):

```fs
victim.health = 20
victim.food = 20
```

Writing anything else throws `EvalException: player property '...' is read-only`.
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

- Globals and per-player variables are saved to `plugins/FastScript/variables.yml`
  (players under `__players__` by UUID) **when the plugin is disabled**,
  not on every change. `$name = <literal>` at the top level also seeds
  missing values on load — saved values always win.
- Writing `#var` with no player in context is an error
  (`no player in context for a player-scoped variable`); reading is `null`.
- `null + 1` arithmetic: `Values.toNumber(null)` throws —
  initialize counters (`#visits = 0` somewhere up front, or guard with a check).
