# Tutorial 2: economy and shop

Let's make coins in `#coins` (each player has their own) plus a shop that
hands out gear for coins. Along the way we'll meet the main variable trap:
`null`.

## The problem: a variable that doesn't exist yet

```fs
#coins = #coins + 10
```

If the player is new, `#coins` is `null`, and `null + 10` is an error:
`cannot use null as a number`. So counters get **initialized**:

```fs
on player join:
    if #coins is null:
        #coins = 100
    message "Balance: " + #coins
```

The `if X is null: X = <start>` pattern is standard for all `#` counters.

## Earning while playing

```fs
on player join:
    if #coins is null:
        #coins = 100

on death:
    #coins = #coins - 10
    message "You lost 10 coins. Balance: " + #coins
```

!!! note "Why no check here"
    After the join handler `#coins` is definitely a number: a living
    player's `death` trigger can't fire before `join`. But if the join
    script is ever deleted, this crashes. For safety, put the `is null`
    check everywhere money is spent.

## The shop

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

Notes:

- Comparing `#coins < price` with `null`? `null < 60` compares as text
  (`""` vs `"60"`) — no error, but no sense either. Initialize first,
  compare second.
- `#coins = #coins - price` is a plain assignment, written back to
  per-player storage immediately.
- Don't forget `shop-sword:` and `balance:` in `plugin.yml`.

## A global tax (a `$` variable example)

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

`$shop-tax` is single for the whole server and survives restarts (written
to `variables.yml` on disable). It changes the same way: `$shop-tax = 10`.
