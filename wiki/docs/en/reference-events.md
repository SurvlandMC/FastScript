# Event reference

Name variants: bare (`join`), with `player` (`player join`), with `event`
(`join event`, `player join event`). Case doesn't matter.

## With a player and interaction text

| Name | Class | Event player | Example |
|---|---|---|---|
| `join` | `PlayerJoinEvent` | the one who joined | greetings, starter kits |
| `quit` | `PlayerQuitEvent` | the one who left | farewells, `broadcast` |
| `chat` | `AsyncPlayerChatEvent` | the author | cooldowns, warnings (**message text is unavailable**) |
| `command` | `PlayerCommandPreprocessEvent` | the command author | command logging |

```fs
on player join:
    broadcast sender-name() + " joined!"

on quit:
    broadcast sender-name() + " left."
```

## Death and respawn

| Name | Class | Event player |
|---|---|---|
| `death` | `PlayerDeathEvent` | the one who died |
| `respawn` | `PlayerRespawnEvent` | the one who respawned |

```fs
on death:
    $kills = $kills + 1
    broadcast sender-name() + " died. Total: " + $kills + "."
```

## World and items

| Name | Class | Event player |
|---|---|---|
| `interact` | `PlayerInteractEvent` | the interacting player |
| `move` | `PlayerMoveEvent` | the moving player (**very frequent!**) |
| `break block` | `BlockBreakEvent` | the breaker |
| `place block` | `BlockPlaceEvent` | the placer |
| `drop item` | `PlayerDropItemEvent` | the dropper |
| `damage` | `EntityDamageEvent` | the entity, if it is a player |

```fs
on damage where sender-name() contains "Steve":
    message "Careful!"
    cancel-event
```

!!! warning "Performance"
    `move` fires on every movement. Don't put anything heavy there:
    no thousand-iteration loops. A guard `if` + `stop` is fine.

!!! note "Coordinates and contents"
    Unavailable in DSL v1: block coordinates, chat text, the held item
    as an object (`item-amount/1` gives only the count). An event provides
    **the fact plus the player**; the rest goes through direct builtins.

## `server start`

A special event with no Bukkit class and no player: it runs once when
the plugin is enabled (after scripts load). Player-dependent code
(`message`, `#var` writes) doesn't work here — there is no player.
`/fastscript reload` does **not** re-fire it (recompiling is not
a server restart):

```fs
on server start:
    $server-started = 1
    log "FastScript routines are ready."
```
