# Типы значений и приведения

## Таблица типов

| Kind | Java-тип | Литерал | `typeName()` |
|---|---|---|---|
| `number` | `Double` (примитив `double` при выводе) | `3`, `7.5` | `"number"` |
| `text` | `String` | `"hi"` | `"text"` |
| `boolean` | `Boolean` (`boolean` при выводе) | `true` | `"boolean"` |
| `list` | `ArrayList` | нет (строить через `list()`/`split`) | `"list"` |
| `map` | `Map` | нет | `"map"` |
| `player` | Bukkit `Player` | нет (через `player()`/`sender()`) | класс |
| `null` | `null` | `null` | `"null"` |

## `toNumber(x)`

`Number` → как есть; `Boolean` → 1/0; `String` → парсинг
(пустая/мусор — `EvalException: cannot use ... as a number`); остальное —
та же ошибка. `null` — ошибка (инициализируйте счётчики!).

## `toBool(x)`

`Boolean` → как есть; число → `!= 0`; текст → непустой **и** не `"false"`
(регистр не важен: `"False"` — тоже false); `null` → `false`;
остальное (списки, игроки) → `true`.

```fs
if "false":     # НЕ выполнится
    message "x"
if "anything":  # выполнится
    message "y"
```

## `toText(x)`

`null` → `""` (пусто, не `"null"`!); целые double — без `.0`
(`5`, а не `5.0`); списки — `[a, b]`; остальное — `String.valueOf`.

```fs
message "Balance: " + #coins   # число красиво
message "Nick: " + victim      # объект игрока — toString(), НЕ ник! используйте name(victim)
```

!!! warning "Объект игрока в тексте"
    Конкатенация `player`-объекта даёт `CraftPlayer{...}`, а не ник.
    Ник — только через `name(p)` / `sender-name()` / `p.name`.

## Равенство `looseEquals`

Оба `null` → `true`; один `null` → `false`; два числа — по значению
(`3 == 3.0` → `true`); есть строка — сравнение текстов (`5 == "5"` → `true`);
иначе `Objects.equals`.

## `for` по не-списку — пустой цикл

`Values.toList` возвращает копию списка, а для всего остального —
**пустой список без ошибки**. `for x in 42:` просто не выполнит ни итерации.
Опечатки в источнике цикла молчат — проверяйте глазами.
