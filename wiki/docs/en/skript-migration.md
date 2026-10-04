# Migrating from Skript

FastScript is not a drop-in replacement: the syntax is similar, but the
language is its own. A table for rewriting typical pieces.

## Equivalents

| Skript | FastScript |
|---|---|
| `on join:` | `on player join:` (variants in the event reference) |
| `command /heal <player>:` + `trigger:` | `command heal(target : text) permission ...:` (the body *is* the handler) |
| `permission: ...` inside a command | `permission <node>` in the command header |
| `send "hi" to player` | `message "hi"` (to the event player) |
| `broadcast "..."` | `broadcast "..."` |
| `give diamond sword to player` | `give "DIAMOND_SWORD" 1` |
| `heal player` / `damage player by 4` | `heal 4` / `damage 4` (event player) or `set-health(p, 20)` |
| `teleport player to ...` | `teleport(x, y, z)` (event player) |
| `kill player` | `kill` |
| `set {x} to 5` | `$x = 5` (global) / `#x = 5` (player) / `let x = 5` (local) |
| `{x}` / `{x::%player%}` | `$x` / `#x` |
| `if ...: else:` | the same |
| `loop 10 times:` | `loop 10 times:` (`loop-index` instead of `loop-number - 1`) |
| `loop ...:` over a list | `for x in list:` |
| `while ...:` | the same |
| `stop` / `return` | the same (`stop` takes no value) |
| `cancel event` | `cancel-event` |
| `%player%` in text | concatenation: `"Hi " + sender-name()` |
| `player's health` | `victim.health` / `sender()` + `.health` |
| `function f(p) :: number:` | `function f(p) : number:` |
| `if player has permission "..."` | `if has-permission(me, "..."):` |

## Migration traps

1. **Stricter indentation**: spaces only (tab = 4), mixed is an error.
2. **No event text**: chat contents, block coordinates are unavailable.
3. **No delays**: there is no replacement for `wait 5 seconds`
   (the scheduler isn't exposed).
4. **Stricter types**: `null + 1` crashes instead of yielding 1 —
   initialize counters.
5. **`is` = `==`**: `is "admin"` is equality, not a type/role check.
6. **Pure functions**: effects and `$`/`#` in them are compile errors.
7. **Commands go in `plugin.yml`**: Skript registers them itself; here, by hand.
8. **The `player` variable**: don't name locals that — you'll shadow the builtin.
9. **`join()` is for lists**: joining two strings uses `+`.
10. **`for` over a non-list is silent**: an empty loop instead of an error.
