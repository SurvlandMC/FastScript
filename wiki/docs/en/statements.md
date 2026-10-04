# Statements

## Local declaration and assignment

```fs
let total = 0
total = total + loop-index
total += 5
list[0] = "first"
```

- `let name = <expression>` — explicit local declaration (`=` required).
- Plain and compound assignment: `= += -= *= /= %=`.
- Targets: local, `$global`, `#player` variable, `list[i]` / `map[key]`.
  Assigning through a dot (`victim.health = ...`) is **not** supported
  by the parser — for health use `set-health(victim, 20)`.
- Postfix increment: `i++`, `i--` (plus prefix `++x`, `--x`).

## Branching

```fs
if victim is null:
    message "Nobody here."
    stop
```

`else` / `else if` are supported, a blank line between branches is allowed:

```fs
if n < 2:
    return n
else if n < 10:
    return n * 2
else:
    return -1
```

## Loops

```fs
while i > 0:
    result = result + i + ","
    i = i - 1

loop n times:
    total = total + loop-index

for friend in online-players():
    message "Online: " + friend
```

- `while <condition>:` — classic loop.
- `loop <N> times:` — exactly N iterations (`times` may be omitted);
  `loop-index` is the zero-based iteration number. The limit and the index
  live in different JVM slots (an old "loop runs once" bug lived right here).
- `for <var> in <list>:` — the source must be a list (`Values.toList`).
  Anything else iterates **zero times, silently**.
- `break` / `continue` — inside loops only, otherwise a compile error.

## Flow control

| Statement | Action |
|---|---|
| `stop` | Stop the handler (also exits a function) |
| `return [expr]` | Return a value from a function |
| `cancel-event` | Effect: cancel the Bukkit event (if cancellable) |

## Effects: calls without parentheses

Effects accept a "bare" form — name plus space/comma-separated arguments:

```fs
message "Healing " + target
give "DIAMOND" 3
log "FastScript routines are ready."
```

The parenthesized equivalent (`message("hi")`) works too, as does a lone
zero-argument name (`kill`). Parsing rule: a `name` followed by the start
of another expression on the same line (text, number, variable,
`true`/`false`/`null`/`not`) is an effect; `name + ...` with an operator
is an expression. Write negative bare arguments in parentheses: `damage(-5)`.

## Ternary

```fs
message "You are " + (victim is null ? "nobody" : victim)
```

Precedence: `?:` is the lowest, branches are full expressions.
