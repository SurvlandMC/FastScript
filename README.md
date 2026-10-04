# FastScript

**Scripts that compile.** FastScript is a fast alternative to Skript for
Paper 1.21.8+ and Leaf servers: you write short scripts in your `plugins`
folder, and the plugin JIT-compiles them to JVM bytecode on load — no
interpreter running on every event.

> 🇷🇺 Русская версия: [README.ru.md](README.ru.md)

## Why

- **Fast** — arithmetic and comparisons become primitive JVM instructions;
  event handling is a direct `MethodHandle` call, no name lookups.
- **Familiar** — Skript-style syntax: `on player join:`, `command heal:`,
  significant indentation.
- **Simple** — no Gradle/Maven, one build script; reload scripts without
  restarting the server.

## Requirements

- Server: Paper 1.21.8+ or Leaf. Java 21+.
- Build machine: JDK 21+, PowerShell.

## Install (60 seconds)

1. Build the plugin (or take a ready-made jar):
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
   ```
   Result: `build\FastScript-1.0.0.jar`.
2. Drop the jar into the server's `plugins/` and restart.
3. Write scripts to `plugins/FastScript/scripts/*.fs`.
4. Duplicate every script command in `plugin.yml` (see below).
5. Apply changes anytime with `/fastscript reload` — no restart needed.

## Your first script

`plugins/FastScript/scripts/hello.fs`:

```fs
on player join:
    message "Welcome to the server!"

command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

And in `plugin.yml`:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
```

## Commands

| Command | What it does |
|---|---|
| `/fastscript reload` (alias `/fs`) | Recompile all scripts |
| `/fastscript scripts` | List loaded scripts |
| `/fastscript variables` | Persistent variable count |
| `/fastscript bench [N]` | Benchmark the first script function |

## Docs

Live docs: **https://wikifs.mc.dc.kg** (English + Russian).

Full language reference, tutorials and cookbook live in the wiki:
[`wiki/`](wiki/) — build it with MkDocs Material or deploy in minutes
with [`wiki/install.sh`](wiki/install.sh) ([deploy guide](wiki/DEPLOY.md),
nginx + systemd, no Docker). The wiki comes in English and Russian.

## Benchmarks

`BenchRunner` compares compiled bytecode against a tree-walking interpreter
of the same AST (`bench/scripts/Arithmetic.fs`): typically ~4x on `fib`,
~12–20x on loops. To compare against Skript on your own server, follow
`bench/skript/README-skript.md` (equivalent `benchmark.sk` included).

## License

Apache-2.0 — see [LICENSE](LICENSE).
