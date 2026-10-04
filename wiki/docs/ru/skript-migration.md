# Миграция со Skript

FastScript — не drop-in замена: синтаксис похож, но язык свой.
Таблица для переписывания типовых кусков.

## Соответствия

| Skript | FastScript |
|---|---|
| `on join:` | `on player join:` (варианты в справочнике событий) |
| `command /heal <player>:` + `trigger:` | `command heal(target : text) permission ...:` (тело — прямо обработчик) |
| `permission: ...` внутри команды | `permission <узел>` в шапке команды |
| `send "hi" to player` | `message "hi"` (игроку события) |
| `broadcast "..."` | `broadcast "..."` |
| `give diamond sword to player` | `give "DIAMOND_SWORD" 1` |
| `heal player` / `damage player by 4` | `heal 4` / `damage 4` (игрок события) или `set-health(p, 20)` |
| `teleport player to ...` | `teleport(x, y, z)` (игрок события) |
| `kill player` | `kill` |
| `set {x} to 5` | `$x = 5` (глобал) / `#x = 5` (игрок) / `let x = 5` (локал) |
| `{x}` / `{x::%player%}` | `$x` / `#x` |
| `if ...: else:` | так же |
| `loop 10 times:` | `loop 10 times:` (`loop-index` вместо `loop-number - 1`) |
| `loop ...:` по списку | `for x in list:` |
| `while ...:` | так же |
| `stop` / `return` | так же (`stop` без значения) |
| `cancel event` | `cancel-event` |
| `%player%` в тексте | конкатенация: `"Hi " + sender-name()` |
| `player's health` | `victim.health` / `sender()` + `.health` |
| `function f(p) :: number:` | `function f(p) : number:` |
| `if player has permission "..."` | `if has-permission(me, "..."):` |

## Ловушки переезда

1. **Отступы строже**: только пробелы (таб = 4), смешанные — ошибка.
2. **Нет текста события**: содержимое чата, координаты блоков недоступны.
3. **Нет задержек**: `wait 5 seconds` нечем заменить (планировщик не выведен).
4. **Типы строже**: `null + 1` падает, а не даёт 1 — инициализируйте счётчики.
5. **`is` = `==`**: `is "admin"` — равенство, не проверка типа/роли.
6. **Функции чистые**: эффекты и `$`/`#` в них запрещены (ошибка компиляции).
7. **Команды — в `plugin.yml`**: Skript регистрирует сам, здесь — руками.
8. **Переменная `player`**: не называйте так локалы — затрете builtin.
9. **`join()` — для списков**: склейка двух строк — через `+`.
10. **`for` по не-списку молчит**: пустой цикл вместо ошибки.
