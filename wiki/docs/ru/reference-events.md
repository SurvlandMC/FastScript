# Справочник событий

Варианты имён: голое (`join`), с `player` (`player join`), с `event`
(`join event`, `player join event`). Регистр не важен.

## С игроком и текстом взаимодействия

| Имя | Класс | Игрок события | Пример |
|---|---|---|---|
| `join` | `PlayerJoinEvent` | вошедший | приветствие, выдача стартового |
| `quit` | `PlayerQuitEvent` | вышедший | прощание, `broadcast` |
| `chat` | `AsyncPlayerChatEvent` | автор | кулдауны, предупреждения (**текст сообщения недоступен**) |
| `command` | `PlayerCommandPreprocessEvent` | автор команды | логирование команд |

```fs
on player join:
    broadcast sender-name() + " joined!"

on quit:
    broadcast sender-name() + " left."
```

## Смерть и возрождение

| Имя | Класс | Игрок события |
|---|---|---|
| `death` | `PlayerDeathEvent` | умерший |
| `respawn` | `PlayerRespawnEvent` | возродившийся |

```fs
on death:
    $kills = $kills + 1
    broadcast sender-name() + " died. Total: " + $kills + "."
```

## Мир и предметы

| Имя | Класс | Игрок события |
|---|---|---|
| `interact` | `PlayerInteractEvent` | взаимодействующий |
| `move` | `PlayerMoveEvent` | движущийся (**очень частое!**) |
| `break block` | `BlockBreakEvent` | ломающий |
| `place block` | `BlockPlaceEvent` | ставящий |
| `drop item` | `PlayerDropItemEvent` | выбросивший |
| `damage` | `EntityDamageEvent` | сущность, если это игрок |

```fs
on damage where sender-name() contains "Steve":
    message "Careful!"
    cancel-event
```

!!! warning "Производительность"
    `move` срабатывает на каждое движение. Не кладите туда тяжёлое:
    никаких циклов на тысячи итераций. Проверка `if` + `stop` — нормально.

!!! note "Координаты и содержимое"
    В DSL v1 недоступны: координаты блоков, текст чата, предмет в руке
    как объект (есть только `item-amount/1` — количество). Событие даёт
    **факт + игрока**, остальное — через прямые builtin'ы.

## `server start`

Особое событие без Bukkit-класса и без игрока: выполняется один раз
при включении плагина (после загрузки скриптов). Игрокозависимое
(`message`, запись `#var`) здесь не работает — игрока нет.
Перезагрузка `/fastscript reload` его **не** повторяет (перекомпиляция —
не рестарт сервера):

```fs
on server start:
    $server-started = 1
    log "FastScript routines are ready."
```
