# Triggers, commands, functions

## Triggers: `on <event> [where <condition>]:`

```fs
on player join:
    message "Welcome to the server!"

on damage where sender-name() contains "Steve":
    message "Careful!"
    cancel-event
```

The event name is free-form text and gets normalized (case and extra spaces
don't matter). Supported events:

| Key | Bukkit event |
|---|---|
| `join` | `PlayerJoinEvent` |
| `quit` | `PlayerQuitEvent` |
| `death` | `PlayerDeathEvent` |
| `respawn` | `PlayerRespawnEvent` |
| `chat` | `AsyncPlayerChatEvent` |
| `command` | `PlayerCommandPreprocessEvent` |
| `interact` | `PlayerInteractEvent` |
| `move` | `PlayerMoveEvent` |
| `break block` | `BlockBreakEvent` |
| `place block` | `BlockPlaceEvent` |
| `drop item` | `PlayerDropItemEvent` |
| `damage` | `EntityDamageEvent` (player — when the entity is a player) |

Every name understands the `player` prefix and the `event` suffix:
`join`, `player join`, `join event`, `player join event` are the same.
All listeners run at `MONITOR` priority (`join`/`chat`/`command`/`interact`/
`move`/`break`/`place`/`drop`/`damage` also use `ignoreCancelled = true`).

An unsupported event fails loading with `unsupported event '...'`.

### The `where` filter

After `where` comes a plain expression on the same line; the colon ends
the header. A false filter quietly ends the handler before its body.
Filters accept the same expressions as everywhere else (e.g. `sender-name()`,
`arg()`, comparisons).

## Commands: `command <name>[(args)] [permission ...] [description ...]:`

```fs
command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

- Arguments are positional text values. The last one annotated `: text`
  is greedy — it captures the rest of the line.
- `permission fastscript.heal` — the required permission (dotted nodes work:
  the parser joins `fastscript` `.` `heal`).
- Inside the handler `args[0]` is the sender; read arguments with `arg(1)`,
  the sender with `sender()` / `sender-name()`.
- **Duplicate every command in `plugin.yml`**, otherwise Bukkit won't bind it
  (the log will say `is not declared in plugin.yml`).

## Functions: `function <name>(...) [: <type>]:`

```fs
function triangle(n) : number:
    let total = 0
    loop n times:
        total = total + loop-index
    return total
```

The return annotation is optional (`: number`, `: text`, `: boolean`,
`: list`, `: map`, `: player`; without it — void-like behavior).
Parameters always arrive as objects.

!!! warning "Functions are pure"
    A function body has no event/command context: effects, `sender()`/`arg()`,
    `$`/`#` variables, `player()`, properties and index reads are compile
    errors there (`... needs an event or command context`). Use inline
    math/text builtins, locals, parameters, comparisons and loops.

## Top-level globals

```fs
$server-started = 0

on server start:
    $server-started = 1
```

`$name = <expression>` at the top level declares a variable with an initial
value (the value is optional). It is shared by every script in the directory.
