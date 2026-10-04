# Error catalog

Format: `file:line:column: message`. Each one below with cause and fix.

## Parsing

| Error | Cause → fix |
|---|---|
| `expected ':'` | Missing colon in a header/condition → add `:` |
| `expected an indented block` | Nothing after `:` → add an indented line |
| `expected at least one statement in the block` | Empty block → at least one statement |
| `inconsistent indentation` | Mixed indents → 4 spaces everywhere |
| `unexpected ... in an expression` | Garbage in an expression; common cause — `#var` with no space read as a variable, or vice versa a comment ate code |
| `unexpected end of line in an expression` | Truncated expression (dangling `+`, unclosed `(`) |
| `unterminated text literal` | Missing closing `"` |
| `expected a variable name after '$'` | `$` + space/end → `$name` must be joined |
| `cannot assign to this expression` | Not a target on the left (`a.b = ...` — properties are read-only) |
| `unknown type '...'` | In a function/argument annotation — only `number text string boolean bool list map player` |
| `unknown command attribute '...'` | After the command name — only `permission`/`description` |
| `expected 'in' after the loop variable` | `for x in ...:` — `in` is missing |
| `'break' outside of a loop` | `break`/`continue` only inside loops |

## Compilation

| Error | Cause → fix |
|---|---|
| `unknown function 'x'` | No builtin and no own function (check arity: `substring/2` vs `/3`) |
| `unknown variable 'x'` | Local not declared; or a shadowed builtin (`let player = ...`); or a lone typo |
| `unknown statement or function 'x'` | A lone unknown name on a line |
| `... needs an event or command context and cannot be used in a function` | Effects, `sender()`/`arg()`, `$`/`#`, `player()`, properties, index reads live only in triggers/commands; functions do pure math |
| `duplicate trigger for event` / `declared twice` | Two identical `on`/`command`/`function` in one file |
| `function 'f' expects N argument(s), got M` | Your own function's arity |
| `argument N of 'x' must be ...` | Type doesn't convert (e.g. a list into `teleport`) |
| `unsupported event '...'` | No such event in the table |

## Runtime (`EvalException`; the handler logs it and carries on)

| Error | Cause |
|---|---|
| `division by zero` (+ `in modulo`) | Zero divisor — instead of `NaN`, deliberately |
| `cannot use X as a number` | Arithmetic with `null`/garbage — initialize |
| `index N is out of bounds` | Index past a list (out-of-range *read* is `null`, *write* is this error) |
| `cannot assign into X` | `x[i] = v` where `x` is not a list/map |
| `unknown function 'x' with N argument(s)` | Dynamic call missed KNOWN (shouldn't happen after compilation) |
| `unknown player property 'x'` | Typo in a property |
| `no player in context...` | `#var` write with no player (`server start`, bench) |
| `player 'x' is not online` | `requirePlayer` with an offline name string |
| `unknown material/potion effect/world` | Garbage in `material()`/`add-effect()`/`world()` |
| `script command '/x' is not declared in plugin.yml` | Plugin warning, not a script error |

## Infrastructure (not yours)

ASM `NegativeArraySizeException` / `VerifyError: Bad type on operand stack`
during assembly are packer bugs (dup/receiver/descriptors) — all known ones
are fixed; if you see one again, look at `emitDispatch`/`emitCall`/`Builtins`.
