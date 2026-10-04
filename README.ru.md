# FastScript

**Скрипты, которые компилируются.** FastScript — быстрая альтернатива Skript
для серверов Paper 1.21.8+ и Leaf: короткие скрипты в папке `plugins`,
которые плагин JIT-компилирует в байткод JVM при загрузке — никакого
интерпретатора на каждое событие.

> 🇬🇧 English version: [README.md](README.md)

## Зачем

- **Быстро** — арифметика и сравнения становятся примитивными инструкциями JVM,
  обработка события — прямой вызов `MethodHandle` без поиска по именам.
- **Привычно** — синтаксис в духе Skript: `on player join:`, `command heal:`,
  значимые отступы.
- **Просто** — без Gradle/Maven, один скрипт сборки; перезагрузка скриптов
  без рестарта сервера.

## Требования

- Сервер: Paper 1.21.8+ или Leaf. Java 21+.
- Машина сборки: JDK 21+, PowerShell.

## Установка (60 секунд)

1. Соберите плагин (или возьмите готовый jar):
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
   ```
   Результат: `build\FastScript-1.0.0.jar`.
2. Положите jar в `plugins/` сервера и перезапустите его.
3. Пишите скрипты в `plugins/FastScript/scripts/*.fs`.
4. Дублируйте каждую команду из скриптов в `plugin.yml` (см. ниже).
5. Применяйте правки командой `/fastscript reload` — рестарт не нужен.

## Первый скрипт

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

И в `plugin.yml`:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
```

## Команды

| Команда | Действие |
|---|---|
| `/fastscript reload` (алиас `/fs`) | Перекомпилировать все скрипты |
| `/fastscript scripts` | Список загруженных скриптов |
| `/fastscript variables` | Число персистентных переменных |
| `/fastscript bench [N]` | Бенчмарк первой функции первого скрипта |

## Документация

Живая документация: **https://wikifs.mc.dc.kg** (русский + английский).

Полный справочник языка, учебники и рецепты — в вики:
[`wiki/`](wiki/) — собирается MkDocs Material, деплой за минуты через
[`wiki/install.sh`](wiki/install.sh) ([гайд](wiki/DEPLOY.ru.md),
nginx + systemd, без Docker). Вики на русском и английском.

## Бенчмарки

`BenchRunner` сравнивает скомпилированный байткод с tree-walking
интерпретатором того же AST (`bench/scripts/Arithmetic.fs`): типично ~4x
на `fib`, ~12–20x на циклах. Сравнение со Skript на своём сервере —
по процедуре `bench/skript/README-skript.md` (эквивалент `benchmark.sk`
в комплекте).

## Лицензия

Apache-2.0 — см. [LICENSE](LICENSE).
