# Триггеры, команды, функции

## Триггеры: `on <событие> [where <условие>]:`

```fs
on player join:
    message "Welcome to the server!"

on damage where sender-name() contains "Steve":
    message "Careful!"
    cancel-event
```

Имя события — свободный текст, нормализуется (регистр и лишние пробелы
не важны). Поддерживаются:

| Ключ | Bukkit-событие |
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
| `damage` | `EntityDamageEvent` (игрок — если сущность игрок) |

Каждое имя понимает префикс `player` и суффикс `event`:
`join`, `player join`, `join event`, `player join event` — одно и то же.
Все слушатели висят с приоритетом `MONITOR` (`join`/`chat`/`command`/`interact`/
`move`/`break`/`place`/`drop`/`damage` — с `ignoreCancelled = true`).

Неподдержанное событие — ошибка загрузки `unsupported event '...'`.

### Фильтр `where`

После `where` — обычное выражение на той же строке, двоеточие завершает шапку.
Ложный фильтр молча завершает обработчик до тела. В фильтре доступны те же
выражения, что и везде (например `sender-name()`, `arg()`, сравнения).

## Команды: `command <имя>[(аргументы)] [permission ...] [description ...]:`

```fs
command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

- Аргументы — позиционные текстовые. Последний с аннотацией `: text` — жадный,
  забирает остаток строки.
- `permission fastscript.heal` — требуемое право (точки поддерживаются:
  `expectAttributeValue` склеивает `fastscript` `.` `heal`). Проверяется дважды:
  Bukkit прячет команду через `plugin.yml`, а плагин перепроверяет при запуске,
  так что достаточно одной открытой декларации среди нескольких скриптов.
- Внутри обработчика `args[0]` — отправитель (`sender()`), `args[1]` — событие
  (в командах пусто); позиционные аргументы команды читаются с `arg(2)`:
  `arg(2)` — первый параметр, `arg(3)` — второй.
- **Каждую команду продублируйте в `plugin.yml`**, иначе Bukkit её не привяжет
  (в логе будет `is not declared in plugin.yml`).

## Функции: `function <имя>(...) [: <тип>]:`

```fs
function triangle(n) : number:
    let total = 0
    loop n times:
        total = total + loop-index
    return total
```

Аннотация возврата необязательна (`: number`, `: text`, `: boolean`,
`: list`, `: map`, `: player`; без неё — `void`-подобное поведение:
неявный `return null`/`0` по kind). Параметры всегда приходят как объекты.

## Глобалы верхнего уровня

```fs
$server-started = 0

on server start:
    $server-started = 1
```

`$name = <выражение>` на верхнем уровне — объявление с начальным значением
(начальное значение опционально). Действует на все скрипты каталога.
Литеральные инициализаторы (`$balance = 100`) засевают отсутствующие значения
при загрузке — сохранённые всегда побеждают; сложное — внутрь обработчиков.
