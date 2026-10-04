# Эксплуатация и бенчмарки

## Админ-команды (`/fastscript`, алиас `/fs`, право `fastscript.admin`)

| Команда | Действие |
|---|---|
| `/fastscript reload` | Перекомпилировать все скрипты (слушатели старого набора снимаются первыми) |
| `/fastscript scripts` | Список скриптов: триггеры/команды/функции |
| `/fastscript variables` | Число персистентных переменных |
| `/fastscript bench [N]` | Замер первой функции первого скрипта (прогрев + N вызовов, нс/вызов) |

## `plugin.yml`: дублирование команд

Bukkit видит только команды из `plugin.yml`. На каждую `command xxx` в скриптах
нужна запись:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
  kit:
    description: Declared by a script
    usage: /kit
```

Без неё в логе: `script command '/xxx' is not declared in plugin.yml`.

## Бенчмарки

`BenchRunner <script.fs> <iterations>` — self-test + сравнение скомпилированного
байткода против tree-walking интерпретатора того же AST (`bench/Interpreter`):
один скрипт (`bench/scripts/Arithmetic.fs`: `fib`, `sum-range`, `countdown`,
`echo-number`, `sum-count`), одинаковые входы, прогрев перед замером.
Ориентир: fib ~4x, sum-range ~12–20x (плавают от машины — нормально).
Честные рамки: один процесс, базовый прогрев, без JMH и доверительных
интервалов — это показывает окупаемость подхода на выбранных операциях,
а не ускорение всего сервера.

Сравнение со Skript делается на своём сервере по процедуре
из `bench/skript/README-skript.md` (там же `benchmark.sk` и `benchmark.fs`):
один алгоритм с обеих сторон, прогрев, 5 прогонов, медиана.

## Ошибки: как читать

- `file.fs:13:5: expected ':'` — `ScriptException.render()`, строка:колонка.
- `unknown function 'x'` — нет builtin'а и нет своей функции с таким именем/арностью.
- `unknown variable 'x'` — локал не объявлен (или затенён builtin — см. `player`).
- `unsupported event '...'` — нет такого события в таблице.
- `division by zero` — вместо `NaN`, осознанно и громко.
- `NegativeArraySizeException` из ASM при сборке массивов аргументов —
  исправлено (`dup` перед `aastore`); если увидите снова — это баг упаковки,
  а не вашего скрипта.

## Известные ограничения

- Глобалы и per-player переменные (`$name`, `#name`) сохраняются
  в `variables.yml` (игроки — под `__players__`) при выключении плагина,
  а не на каждое изменение. Неудачный `/fastscript reload` оставляет
  предыдущий рабочий набор вместо затирания.
- Планировщик (`Host.runLater`) в DSL не выведен.
- `where` — обычное выражение на строке шапки.
- Индексация и `for` — через динамический диспатч, не примитивы; `for` требует список.
- `is` = `==`; `#имя` без пробела — переменная, `# текст` — комментарий.
- Релокация ASM — по явной карте классов (`SimpleRemapper` префиксы не умеет).
