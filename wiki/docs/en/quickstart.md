# Quick start

## 1. Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
# if libs\ is already populated:
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -SkipDependencies
```

Result — `build\FastScript-1.0.0.jar` (~415 KB). ASM is bundled inside,
relocated to `io.github.dsh.fastscript.lib.asm`, so it never conflicts
with other plugins. `paper-api` is intentionally not packaged —
the server provides it at runtime.

!!! warning "Windows PowerShell 5.1"
    Run `.ps1` files only via `powershell -ExecutionPolicy Bypass -File ...` —
    direct execution is blocked by policy. Write files BOM-free:
    `Set-Content -Encoding UTF8` in PowerShell 5.1 adds a BOM that breaks `javac`.

## 2. Install

1. Drop the jar into the server's `plugins/` and restart.
2. A `plugins/FastScript/scripts/` directory appears with `example.fs`.
3. Put your own scripts (`*.fs`) in that directory.
4. Duplicate every script command (`command heal ...`) in `plugin.yml`
   (`commands` section), otherwise Bukkit won't see it.
5. After editing scripts: `/fastscript reload` (alias `/fs reload`).

## 3. First script

```fs
# plugins/FastScript/scripts/hello.fs

on player join:
    message "Welcome to the server!"

command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

Don't forget `plugin.yml`:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
```

## 4. Check without a server

Self-test + benchmark from the built jar (this also proves self-containment
without the original ASM):

```powershell
$root='<repo root>'; $jdk=$env:JAVA_HOME
$libs = @(Get-ChildItem "$root\libs" -Filter *.jar | Where-Object { $_.Name -notmatch '^asm' } | ForEach-Object { $_.FullName })
& "$jdk\bin\java.exe" -cp (("$root\build\FastScript-1.0.0.jar") + ';' + ($libs -join ';')) io.github.dsh.fastscript.bench.BenchRunner "bench\scripts\Arithmetic.fs" 40000
```

Expect `all checks passed` plus `speed-up vs interpreter` lines
(fib ~4x, sum-range ~12–20x; numbers drift with JIT warmup — that's normal).
