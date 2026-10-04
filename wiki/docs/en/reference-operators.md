# Operator reference

## Arithmetic

| Op | Meaning | Types → result | Example |
|---|---|---|---|
| `+` | Addition **or** concatenation | `number+number→number`, otherwise text via `Values.add` | `2 + 3` → `5`; `"n:" + 5` → `"n: 5"` |
| `-` | Subtraction | numbers (`toNumber` when dynamic) | `10 - 4` → `6` |
| `*` | Multiplication | numbers | `3 * 2.5` → `7.5` |
| `/` | Division | numbers; **division by 0 is an error** `division by zero` | `7 / 2` → `3.5` |
| `%` | Remainder | numbers; mod by 0 is `division by zero in modulo` | `7 % 3` → `1` |
| `<<<`, `>>>` | Explicit concatenation | always text | `"a" <<< "b"` → `"ab"` |

`2 + 3 * 4` → `14` (usual precedence). Mixed `ANY`: `a + b + "x"`
stays text because `+` with an unknown operand yields kind `ANY`.

## Comparisons (all return `boolean`)

| Op | Meaning | Note |
|---|---|---|
| `==`, `is` | Equality (`looseEquals`) | `is` is a full synonym, including `is null` |
| `!=` | Inequality | `!looseEquals` |
| `<`, `>`, `<=`, `>=` | Ordering | Numbers numerically, the rest as text |
| `contains` | Membership | List: element present; map: key present; text: substring; `null` → `false` |

`looseEquals`: both `null` → `true`; one `null` → `false`;
two numbers by value; a string on either side compares texts.

```fs
if victim is null:
    stop
if box contains "sword":
    message "Armed!"
if #coins >= 60:
    message "Rich."
```

## Logic

`and` / `&&`, `or` / `||` short-circuit. `!x` / `not x` negates
(the operand goes through `toBool`).

```fs
if n > 0 and n < 10:
    message "Single digit."
if vip or has-permission(me, "shop.vip"):
    message "Welcome!"
```

## Unary and increment

`-x` (numeric minus), `++x` / `--x` / `x++` / `x--` (±1, prefix returns
the new value, postfix the old one). Work on locals, `$`, `#` and indexes.

## Postfix and calls

`.prop` is a property (player) or list `size`; `[k]` indexes
a list/map/string; `f(a, b)` calls your own function or a builtin.

## Assignment

`= += -= *= /= %=` — see [Statements](statements.md).
