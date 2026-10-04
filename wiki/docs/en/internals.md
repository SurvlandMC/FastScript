# How the compiler works (for the advanced)

## The load pipeline

`ScriptLoader.load`: parse every source → generate every class
(the function table is available to each compilation — file order doesn't
matter) → `defineClass` in a child `ScriptClassLoader` (keyed by **binary
name**, not id) → resolve `MethodHandle`s: triggers/commands as
`void(Object[], ExecContext)`, functions via
`MethodType.fromMethodDescriptorString`. One failing script produces
a `LoadException` with all causes at once.

`FASTSCRIPT_DUMP_DIR=path` drops generated `.class` files on disk
for `javap -c` inspection.

## Representations and boxing

`Operators.Rep`: `NUMBER("D")`, `BOOL("Z")`, `TEXT`, `OBJECT`, `VOID`.
Locals always hold references (`Storage.JvmLocal`, kind tracked separately);
converting to `OBJECT` is a no-op; primitive boxing happens in exactly one
place (`boxToObject`/helpers). Boxing is emitted **right after its operand**,
or it lands on the wrong stack slot (`VerifyError: Bad type on operand stack` —
see the history: `emitDispatch`/`pushLikeGet`/index writes lost the array
without `dup`; context calls lacked the `slot 1` receiver;
`give` expected `int` instead of `double`).

## Why number comparisons are special

`Operators.ordered` for `NUMBER`/`NUMBER` is `DCMPG` plus a jump into the
`true` branch: `<`→`IFLT`, `>`→`IFGT`, `<=`→`IFLE`, `>=`→`IFGE` (an inversion
here once flipped every comparison — the benchmark never caught it because
its operands are `ANY` and go through `Values.less/greater...`). Mixed kinds
box both sides into `Values.*` helpers; `resultType` for `+` with an unknown
operand is `ANY`, not `NUMBER`.

## Dispatch and the hot path

`ScriptEngine` keeps one reusable `ExecContext` per script plus a
`spreadInvoker` for triggers/commands: steady state is a handler call with
no reflection and no allocations. `callFunction` (API/bench) boxes arguments
and goes through `invokeWithArguments` — not the hot path, by design.

`Functions.KNOWN` is a `name/arity` list, not a Map: two arities of one name
(`substring/2`, `substring/3`) broke `Map.ofEntries` with a duplicate key.

## ASM relocation

`tools/Package`: the original ASM jars are fed as **inputs** together with
`build/classes` — the packager renames the ASM classes and rewrites the
references (`compiler/Code` already points at the new package). The map is
explicit (`mappingsFor` from the ASM jars): `SimpleRemapper` only does exact
internal-name replacements (verified with `tools/RelocProbe`), no prefixes.

## Network and build

Dependencies are pulled by `tools/Fetch.java` over `java.net.http`
(Maven Central + Paper repo, snapshots, BOM/`dependencyManagement`,
transitive compile/runtime) — so restricted networks don't stop it.
`bungeecord-chat` must stay in `libs`: without it the legacy
`CommandSender.sendMessage` overload doesn't compile (no Adventure
workaround helps — `javac` still needs the class).
