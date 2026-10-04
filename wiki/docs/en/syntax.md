# Syntax and lexing

## File structure

An `.fs` file is a sequence of top-level declarations:
triggers (`on ...:`), commands (`command ...:`), functions (`function ...:`)
and global declarations (`$name = ...`). Blank lines are ignored.

Blocks use **indentation** (4 spaces, tab = 4) and a trailing colon:

```fs
command kit:
    if #kit-ready == false:
        message "Your kit is still cooling down."
        stop
    give "DIAMOND" 3
```

Indentation inside `()[]{}` is ignored — long calls can wrap.
A trailing backslash continues the line.

## Comments

`#` starts a comment, **but** `#name` with no space is a player variable:

```fs
# This is a comment
#visits = #visits + 1   # but this is code: the #visits variable
message "hi" # a trailing comment ends the line like a newline
```

The rule: `#` + letter immediately = variable, `#` + space/end = comment.
A trailing comment after code works like a line break.

## Literals

| Kind | Examples |
|---|---|
| Number (`number`, always double) | `0`, `3`, `7.5`, `.5` |
| Text (`text`) | `"hi"`, `"Healed " + target`, escapes `\n \t \" \\` |
| Boolean (`boolean`) | `true`, `false` |
| Null (`null`) | `null` — a literal, not a variable |

`is` is a synonym of `==`, including null checks: `if victim is null:`.

## Variables

| Form | Scope | Example |
|---|---|---|
| `name` | Local (declared with `let`) | `let victim = player(target)` |
| `$name` | Global, visible to all scripts | `$server-started = 1` |
| `#name` | Per-player, keyed by UUID | `#kit-ready = false` |

Outside a server context (benchmarks) a `#var` with no player reads as `null`.
Assigning to `$`/`#` without prior declaration is fine — that *is*
the declaration. Names may contain `-`: `loop-index`, `#kit-ready`.

!!! danger "Shadowing builtins"
    A local variable hides a builtin function of the same name.
    That's why examples use `let victim = player(target)`, not
    `let player = ...` — otherwise the next `player(...)` call breaks
    with `unknown variable`.
