# Примеры скриптов

Все примеры — целые файлы из `plugins/FastScript/scripts/`, проверены компиляцией.

## 1. Лечение с правом и аргументом

```fs
command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

Не забудьте `heal:` в `plugin.yml`. Последний аргумент `: text` — жадный.

## 2. Счётчик заходов (per-player состояние)

```fs
on player join:
    #visits = #visits + 1
    message "Welcome! This is visit number " + #visits
```

## 3. Кит с кулдауном

```fs
command kit:
    if #kit-ready == false:
        message "Your kit is still cooling down."
        stop
    give "DIAMOND" 3
    #kit-ready = false
    message "Kit granted."
```

## 4. Вычисления: треугольные числа и обратный отсчёт

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

## 5. Глобальное состояние и фильтр

```fs
$server-started = 0

on server start:
    $server-started = 1
    log "FastScript routines are ready."

on damage where sender-name() contains "Steve":
    message "Careful!"
    cancel-event
```
