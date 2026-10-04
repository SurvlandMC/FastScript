# Function reference (all of them, with examples)

Legend: **[D]irect** — compiles to a single typed bytecode call with no
argument packing; **[D]ispatch** — goes through `Functions.dispatch`
(dynamic types get packed into an `Object[]`). The bare form
(`message "hi"`) and parentheses (`message("hi")`) are equivalent;
zero arguments is a lone name (`kill`).

## Messages and logging

```fs
message "Hi!"              # [D]irect, to the event player
broadcast "Restart soon"   # [D]irect, to everyone
log "debug line"           # [D]irect, to the server log
console("note")            # [D]ispatch console/1, same as log but as an expression
title("Win!", "GG")        # [D]irect, title + subtitle for the event player
kick "Spam"                # [D]irect, kick the event player with a reason
sound "ENTITY_PLAYER_LEVELUP"  # [D]irect, sound for the event player
```

Effects act on the **event player**; outside its context (functions —
see below, `server start`) they are quiet no-ops.

## Health, combat, movement

```fs
damage 4                   # [D]irect damage/1, hurts the event player
heal 6                     # [D]irect heal/1, heals the event player
kill                       # [D]irect kill/0, kills the event player
teleport(0, 100, 0)        # [D]irect teleport/3, x y z of the event player
give "DIAMOND" 3           # [D]irect give/2, material + amount (fraction truncated to int, min 1)
set-health(victim, 20)     # [D]ispatch set-health/2, health of a SPECIFIC player (clamped 0..max)
add-effect(victim, "SPEED", 30)  # [D]ispatch add-effect/3, effect, duration in seconds
```

`add-effect`: name as in Bukkit (`PotionEffectType`), case-insensitive;
unknown names throw `EvalException: unknown potion effect '...'` (level is always 0).

## Cancelling and stopping

```fs
cancel-event               # [D]irect, cancel the event (no-op if not cancellable)
stop                       # [D]irect, exit the handler
```

## Context: who called and with what

```fs
let me = sender()               # [D]irect, the sender object (event player in triggers)
message sender-name()           # [D]irect, their name as text
message arg(1)                  # [D]irect, the 1st command argument as text
for p in online-players():      # [D]irect, list of online player objects
    message name(p)             # [D]ispatch name/1, nickname of the object
```

## Players and worlds (dispatcher)

```fs
let victim = player("Steve")    # [D]ispatch player/1, object or null; "self" = current one
let all = players()             # [D]ispatch players/0, online list
message uuid(victim)            # [D]ispatch uuid/1, UUID as string
message distance(a, b)          # [D]ispatch distance/2, between two players
if has-permission(me, "shop.vip"):  # [D]ispatch has-permission/2, alias perm/2
    message "VIP!"
let w = world("world")          # [D]ispatch world/1, world name; unknown throws EvalException
message material("stone")       # [D]ispatch material/1, alias item/1; garbage throws
message item-amount(victim)     # [D]ispatch, held item count (double)
message time()                  # [D]ispatch time/0, ticks of the player's world (0 outside context)
set-time(6000)                  # [D]ispatch set-time/1, noon in the player's world
set-block("world", 1, 2, 3, "STONE")  # [D]ispatch set-block/5
spawn("ZOMBIE", victim)         # [D]ispatch spawn/2, EntityType + player as spawn point
particle("FLAME")               # [D]ispatch particle/1, at the event player
```

`world-players/1` reads the host's `scriptInfo` (empty outside a server).

## Lists

```fs
let e = list()                  # [D]ispatch list/0, empty list
let one = list-of("a")          # [D]ispatch list-of/1, single-element list
message size(box)               # [D]ispatch size/1, alias length/1: list/map/text length
message first(box)              # [D]ispatch, null when empty
message last(box)               # [D]ispatch, null when empty
if box contains "sword":        # operator (also [D]ispatch contains/2 as a function)
    message "Armed!"
```

## Text

```fs
message upper("hi")                 # [D]irect upper/1, "HI" (also [D]ispatch upper/1)
message lower("HI")                 # [D]irect, "hi"
message trim("  a  ")               # [D]ispatch trim/1, "a"
message replace("aaa", "a", "b")    # [D]ispatch replace/3, "bbb"
message substring("hello", 1, 3)    # [D]ispatch substring/2,3, "el" (end optional)
for w in split("a,b", ","):        # [D]ispatch split/2, list of strings
    message w
message join(split("a,b,c", ","), "-")  # [D]irect join/2, "a-b-c": join takes a LIST
message text(123)                   # [D]ispatch text/1, "123"
```

!!! warning "`join` is not concatenation"
    `join(x, sep)` joins the **list** `x` with `sep`; a non-list is
    returned as-is (`join("a","b")` → `"a"`). Concatenate two values
    with `+`: `"a" + "b"`.

## Numbers

```fs
message floor(2.7)    # [D]irect, 2    (also [D]ispatch floor/1)
message ceil(2.1)     # [D]irect, 3
message round(2.5)    # [D]irect, 3 (Math.round)
message abs(0 - 5)    # [D]irect, 5    (also [D]ispatch abs/1)
message sqrt(16)      # [D]irect, 4
message pow(2, 10)    # [D]irect, 1024
message min(3, 7)     # [D]irect, 3    (also [D]ispatch min/2)
message max(3, 7)     # [D]irect, 7
message random(1, 6)  # [D]irect random/2, whole number in [1, 6] inclusive (also [D]ispatch random/1)
message length(box)   # [D]irect length/1, size (also [D]ispatch length/1)
message number("42")  # [D]ispatch number/1, 42
message boolean("x")  # [D]ispatch boolean/1, true (see toBool rules)
```

## Where each may be called

- **Triggers and commands**: everything above.
- **Functions**: pure stuff only — inline math/text forms, your own functions,
  locals, parameters, comparisons, loops. Effects, `sender()`/`arg()`,
  `$`/`#`, `player()`, properties, index reads are compile errors
  (`... needs an event or command context and cannot be used in a function`).
  The reason is architectural: functions have no `ExecContext`/host
  in their descriptor.
