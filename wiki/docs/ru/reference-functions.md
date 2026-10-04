# Справочник функций (все, с примерами)

Легенда: **[П]** — прямой вызов (один типизированный байткод, без упаковки);
**[Д]** — диспетчер `Functions.dispatch`. Bare-форма (`message "hi"`)
и скобки (`message("hi")`) равнозначны; ноль аргументов — голое имя (`kill`).

## Сообщения и лог

```fs
message "Hi!"              # [П] игроку события
broadcast "Restart soon"   # [П] всем
log "debug line"           # [П] в лог сервера
console("note")            # [Д console/1] то же, что log, но как выражение
title("Win!", "GG")        # [П] титул + подтитул игроку события
kick "Spam"                # [П] кикнуть игрока события с причиной
sound "ENTITY_PLAYER_LEVELUP"  # [П] звук игроку события
```

Эффекты действуют на **игрока события**; вне его контекста (функции —
см. ниже, `server start`) — тихий no-op.

## Здоровье, бой, перемещение

```fs
damage 4                   # [П damage/1] урон игроку события
heal 6                     # [П heal/1] лечение игрока события
kill                       # [П kill/0] убить игрока события
teleport(0, 100, 0)        # [П teleport/3] x y z игрока события
give "DIAMOND" 3           # [П give/2] предмет + количество (дробь режется в int, минимум 1)
set-health(victim, 20)     # [Д set-health/2] здоровье КОНКРЕТНОГО игрока (clamp 0..max)
add-effect(victim, "SPEED", 30)  # [Д add-effect/3] эффект, длительность в секундах
```

`add-effect`: имя как в Bukkit (`PotionEffectType`), регистр не важен;
неизвестный — `EvalException: unknown potion effect '...'` (уровень всегда 0).

## Отмена и остановка

```fs
cancel-event               # [П] отменить событие (если отменяемое, иначе no-op)
stop                       # [П] выйти из обработчика
```

## Контекст: кто и с чем вызвал

```fs
let me = sender()               # [П] объект отправителя (в триггере — игрок события)
message sender-name()           # [П] его ник текстом
message arg(1)                  # [П] 1-й аргумент команды текстом
for p in online-players():      # [П] список объектов игроков
    message name(p)             # [Д name/1] ник объекта
```

## Игроки и миры (диспетчер)

```fs
let victim = player("Steve")    # [Д player/1] объект или null; "self" = текущий
let all = players()             # [Д players/0] онлайн-список
message uuid(victim)            # [Д uuid/1] UUID строкой
message distance(a, b)          # [Д distance/2] дистанция (игроки или "self")
if has-permission(me, "shop.vip"):  # [Д has-permission/2], алиас perm/2
    message "VIP!"
let w = world("world")          # [Д world/1] имя мира; неизвестный — EvalException
message material("stone")       # [Д material/1], алиас item/1; мусор — EvalException
message item-amount(victim)     # [Д] количество предмета в руке (double)
message time()                  # [Д time/0] тики мира игрока (0 вне контекста)
set-time(6000)                  # [Д set-time/1] полдень мира игрока
set-block("world", 1, 2, 3, "STONE")  # [Д set-block/5]
spawn("ZOMBIE", victim)         # [Д spawn/2] EntityType + игрок-точка спавна
particle("FLAME")               # [Д particle/1] у игрока события
```

`world-players/1` читает `scriptInfo` хоста (вне сервера — пусто).

## Списки

```fs
let e = list()                  # [Д list/0] пустой список
let one = list-of("a")          # [Д list-of/1] список из ОДНОГО элемента
message size(box)               # [Д size/1], алиас length/1: список/мапа/длина текста
message first(box)              # [Д] null если пусто
message last(box)               # [Д] null если пусто
if box contains "sword":        # оператор (и [Д contains/2] как функция)
    message "Armed!"
```

## Текст

```fs
message upper("hi")                 # [П upper/1] "HI" (и [Д upper/1])
message lower("HI")                 # [П] "hi"
message trim("  a  ")               # [Д trim/1] "a"
message replace("aaa", "a", "b")    # [Д replace/3] "bbb"
message substring("hello", 1, 3)    # [Д substring/2,3] "el" (конец опционален)
for w in split("a,b", ","):         # [Д split/2] список строк
    message w
message join(split("a,b,c", ","), "-")  # [П join/2] "a-b-c": join склеивает СПИСОК
message text(123)                   # [Д text/1] "123"
```

!!! warning "`join` — не конкатенация"
    `join(x, sep)` склеивает **список** `x` через `sep`; не-список
    возвращается как есть (`join("a","b")` → `"a"`). Два значения
    склеивайте через `+`: `"a" + "b"`.

## Числа

```fs
message floor(2.7)    # [П] 2    (и [Д floor/1])
message ceil(2.1)     # [П] 3
message round(2.5)    # [П] 3 (Math.round)
message abs(0 - 5)    # [П] 5    (и [Д abs/1])
message sqrt(16)      # [П] 4
message pow(2, 10)    # [П] 1024
message min(3, 7)     # [П] 3    (и [Д min/2])
message max(3, 7)     # [П] 7
message random(1, 6)  # [П random/2] целое в [1, 6] включительно (и [Д random/1])
message length(box)   # [П length/1] размер (и [Д length/1])
message number("42")  # [Д number/1] 42
message boolean("x")  # [Д boolean/1] true (см. правила toBool)
```

## Где что можно вызывать

- **Триггеры и команды**: всё выше.
- **Функции**: только чистое — математика/текст **[П]**-форм, свои функции,
  локалы, параметры, сравнения, циклы. Эффекты, `sender()/arg()`,
  `$`/`#`, `player()`, свойства, индексы — ошибка компиляции
  `... needs an event or command context and cannot be used in a function`.
  Причина архитектурная: у функций в дескрипторе нет `ExecContext`/хоста.
