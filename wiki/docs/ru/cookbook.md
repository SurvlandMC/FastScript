# Кулинарная книга (рецепты)

Короткие готовые куски. Каждый — вставка в `.fs`-файл; команды не забудьте
дублировать в `plugin.yml`.

## При входе и выходе

```fs
on player join:
    broadcast sender-name() + " joined!"

on quit:
    broadcast sender-name() + " left."
```

## Прощание с причиной смерти

```fs
on death:
    broadcast sender-name() + " died."
    #deaths = #deaths + 1
```

!!! warning ""
    `#deaths = #deaths + 1` упадёт для новичка (`null + 1`)! Добавьте выше:
    `if #deaths is null: #deaths = 0`. См. [учебник 2](tutorial-economy.md).

## Лечение себя и еда

```fs
command feed:
    heal 20
    message "Yum! (food setter есть только в Java-API: PlayerAccess.set)"

command healme:
    let me = sender()
    set-health(me, me.max-health)
    message "Healed."
```

## Проверка прав без `permission:`

```fs
command vipkit:
    let me = sender()
    if has-permission(me, "shop.vip") == false:
        message "VIP only."
        stop
    give "DIAMOND" 5
    message "Enjoy!"
```

## Телепорт к спавну мира

```fs
command spawn:
    teleport(0, 100, 0)
    message "Back to spawn."
```

## Список онлайна одной строкой

```fs
command online:
    let names = ""
    for p in online-players():
        names = names + p + ", "
    message "Online: " + names
```

!!! note "Конкатенация объектов"
    `names + p` превращает объект игрока в текст через `toText`.
    Получится что-то вроде `CraftPlayer{name=Steve}`, а не ник!
    Для ников нужен `name(p)`:
    `names = names + name(p) + ", "`.

Правильный вариант:

```fs
command online:
    let names = ""
    for p in online-players():
        names = names + name(p) + ", "
    message "Online: " + names
```

## Разбор строки по словам

```fs
let words = split("a b c", " ")
message first(words)     # "a"
message last(words)      # "c"
message size(words)      # 3 (размер списка)
```

`size(x)` / `length(x)` — размер списка, мапы или длина текста.
`first` / `last` — пустой список даёт `null`.

## Регистр и чистка ввода

```fs
let nick = trim(arg(1))
if lower(nick) == "admin":
    message "Nice try."
```

## Случайность

```fs
command roll:
    let me = sender()
    let r = random(1, 6)
    message "Rolled: " + r
    if r == 6:
        give "GOLD_INGOT" 1
```

Одноаргументный `random/1` тоже есть (диспетчер), но в командах используйте
двухаргументный — он компилируется в прямой вызов.

## Время суток

```fs
command day:
    set-time(6000)
    message "Set to noon."

command clock:
    message "Time: " + time()
```

`time()` — тики мира игрока события (вне контекста игрока — 0).

## Титул победителю

```fs
command win:
    title("Victory!", sender-name() + " wins!")
```

## Отмена урона себе

```fs
on damage:
    cancel-event
    message "You are protected."
```

## Блок неделимого спавна (заглушка под координаты)

```fs
on place block:
    message "Block placed. (coords unavailable in DSL v1)"
```

Честно: координат блока в DSL v1 нет — только факт события.
Не пишите проверки «если блок в регионе» — нечем сравнивать.

## Счётчик убийств общий

```fs
$ kills = 0
```

!!! danger "Пробел после `$`?"
    Нет! `$ kills` — ошибка. Только `$kills`. Пробел допустим только
    вокруг операторов: `$kills = $kills + 1`.

```fs
$kills = 0

on death:
    $kills = $kills + 1
    broadcast "Total deaths: " + $kills + "."
```
