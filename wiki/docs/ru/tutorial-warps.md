# Учебник 3: варпы без мап

В DSL нет литералов мап, поэтому координаты варпа храним **текстом**
`"мир,x,y,z"`, а разбираем через `split` и индексы. Приём универсальный:
любая структура, которую можно закодировать строкой.

## Точка дома

```fs
command sethome:
    $home = sender-world() + ",100,64,200"
    message "Home set (demo coords)."

command home:
    if $home is null:
        message "No home set. Use /sethome first."
        stop
    let parts = split($home, ",")
    teleport(parts[1], parts[2], parts[3])
    message "Teleported home."
```

По строкам:

- `split($home, ",")` возвращает список `["world", "100", "64", "200"]`.
- `parts[1]` — индексация списка (0-based). `parts[0]` — мир (здесь не нужен,
  `teleport` телепортирует в текущем мире игрока).
- `teleport` принимает числа; текстовые `"100"` приводятся в числа
  автоматически (`text → number` разрешён для аргументов).
- `teleport` действует на игрока события — в команде это отправитель.

!!! warning "Проверка длины"
    Если `$home` записан криво (`"world,100"`), `parts[2]` вернёт `null`,
    а `teleport` с `null` упадёт. Для своих файлов достаточно писать
    координаты аккуратно; для чужих — проверяйте `length(parts)`:
    `if length(parts) < 4: ...`.

```fs
command sethome:
    let me = sender()
    $home = me.world + "," + me.x + "," + me.y + "," + me.z
    message "Home set at " + $home + "."

command home:
    if $home is null:
        message "No home set. Use /sethome first."
        stop
    let parts = split($home, ",")
    if length(parts) < 4:
        message "Home data is broken."
        stop
    teleport(parts[1], parts[2], parts[3])
    message "Teleported home."
```

`sender()` возвращает объект игрока, `.world`/`.x`/`.y`/`.z` — его свойства.
Конкатенация с числом даёт текст автоматически.

## Несколько варпов

```fs
command setwarp(name : text):
    let me = sender()
    $warp = me.world + "," + me.x + "," + me.y + "," + me.z
    message "Warp '" + name + "' saved (demo: single slot)."

command warp(name : text):
    if $warp is null:
        message "No warp saved."
        stop
    let parts = split($warp, ",")
    teleport(parts[1], parts[2], parts[3])
    message "Warped to '" + name + "'."
```

Честное замечание: это **один слот** (`$warp`), а не словарь варпов —
именованных слотов в языке нет, потому что нет литералов мап.
Для 2–3 фиксированных точек заведите `$warp-home`, `$warp-spawn`, `$warp-shop`.
