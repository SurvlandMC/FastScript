# Cookbook (recipes)

Short ready-made snippets. Each goes into an `.fs` file; don't forget
to duplicate commands in `plugin.yml`.

## Join and quit

```fs
on player join:
    broadcast sender-name() + " joined!"

on quit:
    broadcast sender-name() + " left."
```

## Farewell with a death cause

```fs
on death:
    broadcast sender-name() + " died."
    #deaths = #deaths + 1
```

!!! warning ""
    `#deaths = #deaths + 1` crashes for newcomers (`null + 1`)! Add above:
    `if #deaths is null: #deaths = 0`. See [Tutorial 2](tutorial-economy.md).

## Feeding and healing yourself

```fs
command feed:
    heal 20
    message "Yum!"

command healme:
    let me = sender()
    set-health(me, me.max-health)
    message "Healed."
```

## Permission check without `permission:`

```fs
command vipkit:
    let me = sender()
    if has-permission(me, "shop.vip") == false:
        message "VIP only."
        stop
    give "DIAMOND" 5
    message "Enjoy!"
```

## Teleport to world spawn

```fs
command spawn:
    teleport(0, 100, 0)
    message "Back to spawn."
```

## Online list in one line

```fs
command online:
    let names = ""
    for p in online-players():
        names = names + p + ", "
    message "Online: " + names
```

!!! note "Concatenating objects"
    `names + p` renders a player object via `toText` — something like
    `CraftPlayer{name=Steve}`, not the nick! For nicks use `name(p)`:
    `names = names + name(p) + ", "`.

Correct version:

```fs
command online:
    let names = ""
    for p in online-players():
        names = names + name(p) + ", "
    message "Online: " + names
```

## Splitting a string into words

```fs
let words = split("a b c", " ")
message first(words)     # "a"
message last(words)      # "c"
message size(words)      # 3 (list size)
```

`size(x)` / `length(x)` is the list/map size or text length.
`first` / `last` of an empty list give `null`.

## Case and input cleanup

```fs
let nick = trim(arg(2))
if lower(nick) == "admin":
    message "Nice try."
```

## Rolling dice

```fs
command roll:
    let r = random(1, 6)
    message "Rolled: " + r
    if r == 6:
        give "GOLD_INGOT" 1
```

## Time of day

```fs
command day:
    set-time(6000)
    message "Set to noon."

command clock:
    message "Time: " + time()
```

`time()` is the event player's world ticks (0 outside player context).

## Title for the winner

```fs
command win:
    title("Victory!", sender-name() + " wins!")
```

## Cancelling damage on yourself

```fs
on damage:
    cancel-event
    message "You are protected."
```

## Spawn block protection (placeholder for coordinates)

```fs
on place block:
    message "Block placed. (coords unavailable in DSL v1)"
```

Honestly: no block coordinates exist in DSL v1 — only the fact of the event.
Don't write "if block in region" checks — there's nothing to compare with.

## Shared kill counter

```fs
$kills = 0
```

!!! danger "Space after `$`?"
    No! `$ kills` is an error. Only `$kills`. Spaces are allowed
    around operators only: `$kills = $kills + 1`.

```fs
$kills = 0

on death:
    $kills = $kills + 1
    broadcast "Total deaths: " + $kills + "."
```
