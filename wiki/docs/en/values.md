# Value types and coercion

## Type table

| Kind | Java type | Literal | `typeName()` |
|---|---|---|---|
| `number` | `Double` (`double` primitive when inferred) | `3`, `7.5` | `"number"` |
| `text` | `String` | `"hi"` | `"text"` |
| `boolean` | `Boolean` (`boolean` when inferred) | `true` | `"boolean"` |
| `list` | `ArrayList` | none (build via `list()`/`split`) | `"list"` |
| `map` | `Map` | none | `"map"` |
| `player` | Bukkit `Player` | none (via `player()`/`sender()`) | class name |
| `null` | `null` | `null` | `"null"` |

## `toNumber(x)`

`Number` as-is; `Boolean` → 1/0; `String` gets parsed
(empty/garbage is `EvalException: cannot use ... as a number`); anything
else is the same error. `null` is an error — initialize your counters!

## `toBool(x)`

`Boolean` as-is; number is `!= 0`; text is non-empty **and** not `"false"`
(case-insensitive: `"False"` is false too); `null` → `false`;
everything else (lists, players) → `true`.

```fs
if "false":     # does NOT run
    message "x"
if "anything":  # runs
    message "y"
```

## `toText(x)`

`null` → `""` (empty, not `"null"`!); integral doubles without `.0`
(`5`, not `5.0`); lists as `[a, b]`; the rest via `String.valueOf`.

```fs
message "Balance: " + #coins   # numbers render nicely
message "Nick: " + victim      # a player object renders as toString(), NOT the nick! use name(victim)
```

!!! warning "Player objects in text"
    Concatenating a `player` object yields `CraftPlayer{...}`, not the nickname.
    The nick comes only from `name(p)` / `sender-name()` / `p.name`.

## Equality `looseEquals`

Both `null` → `true`; one `null` → `false`; two numbers by value
(`3 == 3.0` → `true`); a string on either side compares texts
(`5 == "5"` → `true`); otherwise `Objects.equals`.

## `for` over a non-list is an empty loop

`Values.toList` returns a copy of a list, and for anything else —
**an empty list with no error**. `for x in 42:` simply runs zero iterations.
Typos in the loop source stay silent — double-check them visually.
