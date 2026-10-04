# Учебник 1: первый скрипт за 10 минут

Этот учебник проведёт от пустого сервера до рабочего хила и кита.
Каждый шаг можно проверить сразу — перезапуск сервера не нужен.

## Шаг 0. Что уже должно быть

Плагин собран (`build/FastScript-1.0.0.jar`), сервер Paper 1.21.8+ запущен,
jar лежит в `plugins/`, в консоли видно:

```text
FastScript enabled: 1 script(s), 1 trigger(s), 1 script command(s)
```

Каталог скриптов: `plugins/FastScript/scripts/`.

## Шаг 1. Приветствие зашедшим

Создайте файл `plugins/FastScript/scripts/hello.fs`:

```fs
on player join:
    message "Welcome to the server!"
```

В консоли или игре:

```text
/fastscript reload
```

Зайдите вторым аккаунтом (или попросите друга) — придёт приветствие.
`message` без получателя всегда пишет **игроку события**.

## Шаг 2. Команда с аргументом

Допишите в тот же файл:

```fs
command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

Разбор построчно:

- `command heal(target : text)` — команда `/heal <текст>`, последний текстовый
  аргумент жадный (забирает остаток строки).
- `permission fastscript.heal` — без права команда не выполнится на уровне Bukkit.
- `player(target)` — поиск онлайн-игрока, вернёт объект или `null`.
- `is null` — синоним `== null`.
- `stop` — досрочный выход из обработчика.

Теперь **обязательно** добавьте команду в `plugin.yml` плагина:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
```

Без этого Bukkit команду не привяжет, а в логе будет:

```text
script command '/heal' is not declared in plugin.yml
```

Перезагрузите скрипты (`/fastscript reload`) и проверьте:
`/heal Steve`, `/heal Nobody123`.

## Шаг 3. Кит с кулдауном на `#`-переменной

```fs
command kit:
    if #kit-ready == false:
        message "Your kit is still cooling down."
        stop
    give "DIAMOND" 3
    #kit-ready = false
    message "Kit granted."
```

`#kit-ready` — переменная **в разрезе игрока** (ключ — UUID).
У каждого игрока своё значение, чужие кулдауны не затрагиваются.
Первый раз переменная равна `null`, а `null == false` — это `false`,
поэтому новичок кит получит. И не забудьте `kit:` в `plugin.yml`.

## Что дальше

- [Учебник 2: экономика и магазин](tutorial-economy.md) — счётчики,
  инициализация `null`, арифметика переменных.
- [Учебник 3: варпы](tutorial-warps.md) — хранение координат в тексте,
  `split`, индексы, `teleport`.
- [Учебник 4: анти-спам](tutorial-automod.md) — `time()`, `cancel-event`.
