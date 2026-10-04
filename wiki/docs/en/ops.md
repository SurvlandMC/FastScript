# Operations and benchmarks

## Admin commands (`/fastscript`, alias `/fs`, permission `fastscript.admin`)

| Command | Action |
|---|---|
| `/fastscript reload` | Recompile all scripts (old listeners are removed first) |
| `/fastscript scripts` | List scripts: triggers/commands/functions |
| `/fastscript variables` | Persistent variable count |
| `/fastscript bench [N]` | Benchmark the first function of the first script (warmup + N calls, ns/call) |

## `plugin.yml`: duplicating commands

Bukkit only sees commands from `plugin.yml`. Every script `command xxx`
needs an entry:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
  kit:
    description: Declared by a script
    usage: /kit
```

Without it the log says: `script command '/xxx' is not declared in plugin.yml`.

## Benchmarks

`BenchRunner <script.fs> <iterations>` is a self-test plus a comparison
of compiled bytecode against a tree-walking interpreter of the same AST
(`bench/Interpreter`): one script (`bench/scripts/Arithmetic.fs`: `fib`,
`sum-range`, `countdown`, `echo-number`, `sum-count`), identical inputs,
warmup before measuring. Rule of thumb: fib ~4x, sum-range ~12–20x
(drifts between machines — by design).

Comparing against Skript is done on your own server following
`bench/skript/README-skript.md` (with `benchmark.sk` and `benchmark.fs`):
same algorithm on both sides, warmup, 5 runs, median.

## Reading errors

- `file.fs:13:5: expected ':'` — that's `ScriptException.render()`, line:column.
- `unknown function 'x'` — no builtin and no own function with that name/arity.
- `unknown variable 'x'` — local not declared (or a shadowed builtin — see `player`).
- `unsupported event '...'` — no such event in the table.
- `division by zero` — instead of `NaN`, deliberately loud.
- ASM `NegativeArraySizeException` while packing argument arrays is fixed
  (`dup` before `aastore`); if you see it again, it's a packer bug,
  not your script.

## Known limitations

- Globals (`$name`) are saved to `variables.yml` when the plugin is
  disabled, not on every change.
- The scheduler (`Host.runLater`) exists in the runtime but is not exposed
  in the DSL.
- The `where` filter (`on damage where ...`) parses as a plain expression.
- Indexing and `for` go through dynamic dispatch, not primitives;
  `for` requires a list.
- `is` = `==`; `#name` with no space is a variable, `# text` is a comment.
- ASM relocation uses an explicit class map (`SimpleRemapper` can't do prefixes).
