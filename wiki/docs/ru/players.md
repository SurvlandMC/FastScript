# Игроки и переменные

## Откуда берётся игрок

- В триггере/команде эффекты без получателя (`message`, `heal`, `give` …)
  действуют на **игрока события** автоматически.
- `sender()` — объект отправителя (в триггере — игрок события),
  `sender-name()` — его имя текстом.
- `player(name)` — резолв онлайн-игрока по нику в объект; `null`, если оффлайн:

```fs
let victim = player(target)
if victim is null:
    message "Player '" + target + "' is not online."
    stop
message victim.name + " has " + victim.health + " hp"
```

## Свойства игрока (чтение через точку)

`name`, `display-name`, `health`, `max-health`, `food`, `level`, `exp`,
`x`, `y`, `z`, `yaw`, `pitch`, `world`, `gamemode`, `uuid`,
`is-op`, `is-flying`, `is-sneaking`, `is-sprinting`, `is-online`,
`ping`, `ip`.

Свойства читаются через точку, а записываемые — ещё и пишутся:
`health`, `food`, `level`, `exp`, `display-name`, `flying`, `sneaking`,
`sprinting` (запись идёт напрямую через `PlayerAccess.set`):

```fs
victim.health = 20
victim.food = 20
```

Запись всего остального — `EvalException: player property '...' is read-only`.
Неизвестное свойство — `EvalException: unknown player property '...'`.

## Переменные: `$`, `#`, персистентность

| Запись | Видимость | Хранение |
|---|---|---|
| `$name` | Все скрипты | `variables.yml`, переживает рестарт |
| `#name` | Один игрок (по UUID) | Память (снапшот доступен, в yml не пишется) |
| `name` | Локал вызова | JVM-локал, быстрее всего |

```fs
$server-started = 0

on server start:
    $server-started = 1

on player join:
    #visits = #visits + 1
    message "Visit #" + #visits
```

Правила:

- Глобалы и per-player переменные сохраняются в `plugins/FastScript/variables.yml`
  (игроки — под `__players__` по UUID) **при выключении плагина**,
  а не на каждое изменение. `$name = <литерал>` на верхнем уровне также
  инициализирует отсутствующие значения при загрузке — сохранённые всегда побеждают.
- Запись `#var` без игрока в контексте — ошибка
  (`no player in context for a player-scoped variable`); чтение — `null`.
- Арифметика `null + 1`: `Values.toNumber(null)` бросит исключение —
  инициализируйте счётчики (`#visits = 0` где-то заранее или через проверку).
