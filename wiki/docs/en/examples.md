# Script examples

All examples are whole files for `plugins/FastScript/scripts/`, compile-checked.

## 1. Heal with permission and argument

```fs
command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

Don't forget `heal:` in `plugin.yml`. The last `: text` argument is greedy.

## 2. Join counter (per-player state)

```fs
on player join:
    #visits = #visits + 1
    message "Welcome! This is visit number " + #visits
```

## 3. Kit with cooldown

```fs
command kit:
    if #kit-ready == false:
        message "Your kit is still cooling down."
        stop
    give "DIAMOND" 3
    #kit-ready = false
    message "Kit granted."
```

## 4. Computation: triangular numbers and countdown

```fs
function triangle(n) : number:
    let total = 0
    loop n times:
        total = total + loop-index
    return total

function countdown(from) : text:
    let result = ""
    let i = from
    while i > 0:
        result = result + i + ","
        i = i - 1
    return result
```

## 5. Global state and a filter

```fs
$server-started = 0

on server start:
    $server-started = 1
    log "FastScript routines are ready."

on damage where sender-name() contains "Steve":
    message "Careful!"
    cancel-event
```
