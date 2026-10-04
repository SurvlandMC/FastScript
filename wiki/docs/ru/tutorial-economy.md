# Учебник 2: экономика и магазин

Сделаем монеты на `#coins` (у каждого свои) и магазин, где за монеты
выдаётся экипировка. Попутно разберём главную ловушку переменных: `null`.

## Проблема: переменная, которой ещё нет

```fs
#coins = #coins + 10
```

Если игрок зашёл впервые, `#coins` равно `null`, а `null + 10` — ошибка
`cannot use null as a number`. Поэтому счётчики **инициализируют**:

```fs
on player join:
    if #coins is null:
        #coins = 100
    message "Balance: " + #coins
```

Паттерн `if X is null: X = <старт>` — стандартный для всех `#`-счётчиков.

## Начисление за игру

```fs
on player join:
    if #coins is null:
        #coins = 100

on death:
    #coins = #coins - 10
    message "You lost 10 coins. Balance: " + #coins
```

!!! note "Почему здесь без проверки"
    После join-хендлера `#coins` уже точно число: триггер `death` у живого
    игрока не может сработать раньше `join`. Но если скрипт join'а удалят —
    упадёт. Для надёжности проверку `is null` ставят в каждом месте траты.

## Магазин

```fs
command shop-sword:
    if #coins is null:
        #coins = 100
    let price = 60
    if #coins < price:
        message "Need " + price + ", you have " + #coins + "."
        stop
    #coins = #coins - price
    give "DIAMOND_SWORD" 1
    message "Bought! Balance: " + #coins

command balance:
    if #coins is null:
        #coins = 100
    message "Balance: " + #coins
```

Что важно:

- Сравнение `#coins < price` работает прямо с `null`? Нет — `null < 60`
  сравнивает как текст (`""` vs `"60"`), ошибки не будет, но и смысла мало.
  Поэтому сначала инициализация, потом сравнение.
- `#coins = #coins - price` — обычное присваивание, пишется обратно
  в per-player хранилище сразу.
- Не забудьте `shop-sword:` и `balance:` в `plugin.yml`.

## Глобальный налог (пример `$`-переменной)

```fs
$shop-tax = 5

command shop-sword:
    if #coins is null:
        #coins = 100
    let price = 60 + $shop-tax
    if #coins < price:
        message "Need " + price + ", you have " + #coins + "."
        stop
    #coins = #coins - price
    give "DIAMOND_SWORD" 1
    message "Bought with tax! Balance: " + #coins
```

`$shop-tax` одна на весь сервер и переживает рестарт (пишется
в `variables.yml` при выключении). Меняется так же: `$shop-tax = 10`.
