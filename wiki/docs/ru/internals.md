# Как устроен компилятор (для продвинутых)

## Конвейер загрузки

`ScriptLoader.load`: парсинг всех исходников → генерация всех классов
(таблица функций доступна каждой компиляции — порядок файлов не важен) →
`defineClass` в дочернем `ScriptClassLoader` (ключ — **бинарное имя**, не id) →
резолв `MethodHandle`: триггеры/команды типом `void(Object[], ExecContext)`,
функции — `MethodType.fromMethodDescriptorString`. Ошибка любого скрипта —
`LoadException` со всеми причинами сразу.

`FASTSCRIPT_DUMP_DIR=path` — сложить сгенерированные `.class` на диск
для `javap -c`.

## Представления и бокс

`Operators.Rep`: `NUMBER("D")`, `BOOL("Z")`, `TEXT`, `OBJECT`, `VOID`.
Локалы всегда хранят ссылки (`Storage.JvmLocal`, kind отдельно);
конвертация в `OBJECT` — no-op; бокс примитивов — ровно в одном месте
(`boxToObject`/хелперы). Бокс эмитится **сразу после операнда**, иначе ляжет
не на тот стек (`VerifyError: Bad type on operand stack` — см. историю:
`emitDispatch`/`pushLikeGet`/индексная запись теряли массив без `dup`;
контекстным вызовам не хватало receiver'а `slot 1`;
`give` ждал `int` вместо `double`).

## Почему сравнения с числами особенные

`Operators.ordered` для `NUMBER`/`NUMBER` — `DCMPG` + прыжок в `true`-ветку:
`<`→`IFLT`, `>`→`IFGT`, `<=`→`IFLE`, `>=`→`IFGE` (инверсия здесь когда-то
переворачивала все сравнения — бенч не ловил, т.к. там операнды `ANY`
идут через `Values.less/greater...`). Смешанные kinds — боксинг обоих
и `Values.*`; `resultType` для `+` с неизвестным — `ANY`, не `NUMBER`.

## Диспетч и хот-путь

`ScriptEngine` держит переиспользуемый `ExecContext` на скрипт и
`spreadInvoker` для триггеров/команд: steady-state — вызов хендлера
без рефлексии и аллокаций. `callFunction` (API/бенч) боксит аргументы
и идёт через `invokeWithArguments` — не горячий путь, так задумано.

`Functions.KNOWN` — список `имя/арность`, а не Map: две арности одного имени
(`substring/2`, `substring/3`) ломали `Map.ofEntries` дубликатом ключа.

## Релокация ASM

`tools/Package`: оригинальные ASM-джарники подаются **входами** вместе
с `build/classes` — упаковщик переименовывает классы ASM и переписывает
ссылки (`compiler/Code` уже смотрит на новый пакет). Карта — явная
(`mappingsFor` из ASM-джарников): `SimpleRemapper` умеет только точные
замены internal-name (проверено `tools/RelocProbe`), префиксы — нет.

## Сеть и сборка

Зависимости тянет `tools/Fetch.java` через `java.net.http`
(Maven Central + Paper repo, снапшоты, BOM/`dependencyManagement`,
транзитивные compile/runtime) — поэтому бетховская сеть ей не мешает.
`bungeecord-chat` в `libs` обязателен: без него не компилируется
легаси-overload `CommandSender.sendMessage` (обход через Adventure
не работает — `javac` всё равно требует класс).
