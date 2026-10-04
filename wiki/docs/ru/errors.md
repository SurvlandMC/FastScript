# Каталог ошибок

Формат: `файл:строка:колонка: сообщение`. Ниже — каждая с причиной и фиксом.

## Парсинг

| Ошибка | Причина → фикс |
|---|---|
| `expected ':'` | Нет двоеточия в шапке/условии → допишите `:` |
| `expected an indented block` | После `:` нет блока → добавьте строку с отступом |
| `expected at least one statement in the block` | Пустой блок → хоть один стейтмент |
| `inconsistent indentation` | Смешаны отступы → везде 4 пробела |
| `unexpected ... in an expression` | Мусор в выражении; частая причина — `#var` без пробела воспринят как переменная, или наоборот комментарий съел код |
| `unexpected end of line in an expression` | Выражение оборвано (висячий `+`, незакрытая `(`) |
| `unterminated text literal` | Нет закрывающей `"` |
| `expected a variable name after '$'` | `$` + пробел/конец → `$name` слитно |
| `cannot assign to this expression` | Слева не цель (`a.b = ...` — свойства только читаются) |
| `unknown type '...'` | В аннотации функции/аргумента — только `number text string boolean bool list map player` |
| `unknown command attribute '...'` | После имени команды — только `permission`/`description` |
| `expected 'in' after the loop variable` | `for x in ...:` — пропущен `in` |
| `'break' outside of a loop` | `break`/`continue` только в циклах |

## Компиляция

| Ошибка | Причина → фикс |
|---|---|
| `unknown function 'x'` | Нет builtin'а и своей функции (проверьте арность: `substring/2` vs `/3`) |
| `unknown variable 'x'` | Локал не объявлен; или затёрт builtin (`let player = ...`); или lone-опечатка |
| `unknown statement or function 'x'` | Одинокое неизвестное имя на строке |
| `... needs an event or command context and cannot be used in a function` | Эффекты, `sender()/arg()`, `$`/`#`, `player()`, свойства, индексы — только в триггерах/командах; в функциях — чистая математика |
| `duplicate trigger for event` / `declared twice` | Два одинаковых `on`/`command`/`function` в файле |
| `function 'f' expects N argument(s), got M` | Арность своей функции |
| `argument N of 'x' must be ...` | Тип не приводится (например, список в `teleport`) |
| `unsupported event '...'` | Нет такого события (см. справочник событий) |

## Рантайм (`EvalException`, handler пишет в лог и продолжает)

| Ошибка | Причина |
|---|---|
| `division by zero` (+ `in modulo`) | Делитель 0 — вместо `NaN`, осознанно |
| `cannot use X as a number` | Арифметика с `null`/мусором — инициализируйте |
| `index N is out of bounds` | Индекс мимо списка (вне — `null` только при чтении через `index()`? нет: запись — ошибка, чтение мимо — `null`) |
| `cannot assign into X` | `x[i] = v`, где `x` не список/мапа |
| `unknown function 'x' with N argument(s)` | Динамический вызов мимо KNOWN (не должно случаться после компиляции) |
| `unknown player property 'x'` | Опечатка в свойстве |
| `no player in context...` | Запись `#var` без игрока (`server start`, бенч) |
| `player 'x' is not online` | `requirePlayer` со строкой оффлайна |
| `unknown material/potion effect/world` | Мусор в `material()/add-effect()/world()` |
| `script command '/x' is not declared in plugin.yml` | Предупреждение плагина, не ошибка скрипта |

## Инфраструктурные (не ваши)

`NegativeArraySizeException` / `VerifyError: Bad type on operand stack`
из ASM при сборке — баги упаковки (dup/receiver/дескрипторы), все известные
исправлены; если увидите снова — смотрите `emitDispatch`/`emitCall`/`Builtins`.
