# Быстрый старт

## 1. Сборка

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
# если libs\ уже заполнен:
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -SkipDependencies
```

Результат — `build\FastScript-1.0.0.jar` (~415 КБ). ASM уже внутри,
за-reloc-ирован в `io.github.dsh.fastscript.lib.asm`, конфликтов с другими
плагинами не будет. `paper-api` в jar не пакуется — его предоставляет сервер.

!!! warning "Windows PowerShell 5.1"
    Файлы `.ps1` запускайте только через
    `powershell -ExecutionPolicy Bypass -File ...` — прямое выполнение
    заблокировано политикой. Файлы без BOM: `Set-Content -Encoding UTF8`
    в PowerShell 5.1 добавляет BOM, который ломает `javac`.

## 2. Установка

1. Положите jar в `plugins/` сервера и перезапустите сервер.
2. Появится каталог `plugins/FastScript/scripts/` с `example.fs`.
3. Свои скрипты — файлы `*.fs` в этом каталоге.
4. Каждую команду из скриптов (`command heal ...`) продублируйте
   в `plugin.yml` (секция `commands`), иначе Bukkit её не увидит.
5. После правок скриптов: `/fastscript reload` (алиас `/fs reload`).

## 3. Первый скрипт

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

Не забудьте в `plugin.yml`:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
```

## 4. Проверка без сервера

Self-test + бенчмарк из собранного jar (заодно проверяет самодостаточность
без оригинального ASM):

```powershell
$root='<корень репозитория>'; $jdk=$env:JAVA_HOME
$libs = @(Get-ChildItem "$root\libs" -Filter *.jar | Where-Object { $_.Name -notmatch '^asm' } | ForEach-Object { $_.FullName })
& "$jdk\bin\java.exe" -cp (("$root\build\FastScript-1.0.0.jar") + ';' + ($libs -join ';')) io.github.dsh.fastscript.bench.BenchRunner "bench\scripts\Arithmetic.fs" 40000
```

Ожидается `all checks passed` и строки `speed-up vs interpreter`
(fib ~4x, sum-range ~12–20x; плавают от прогрева JIT — это нормально).
