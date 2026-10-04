# FastScript — scripts that compile

FastScript is an optimized Skript alternative for **Paper 1.21.8+**
and **Leaf** servers. Scripts are written in a small Skript-like DSL and
**JIT-compiled to JVM bytecode** (via ASM) on load — one Java class per script.
After that, events are handled by plain compiled code, not an interpreter:
a ready `MethodHandle` is invoked with no name lookups, no reflection,
and no boxing where the type could be proven statically.

## How it works (in short)

1. **Parsing** — the `Lexer` turns text into tokens (with synthetic
   `INDENT`/`DEDENT`), the `Parser` builds an AST.
2. **Type inference** — `Compiler.kindOf` finds the kind of every expression.
   `number` + `number` becomes primitive `DADD`/`DCMPL` instructions,
   everything dynamic goes to `Values.*` helpers.
3. **Generation** — one static method per trigger, command and function;
   loop state lives in JVM locals, not dictionaries.
4. **Loading** — `ScriptLoader` defines classes in a child `ClassLoader`,
   `ScriptEngine` resolves each `MethodHandle` once and then just calls them.

See also: [Quick start](quickstart.md), [Syntax](syntax.md),
[Operations and benchmarks](ops.md).

## Where is what in this wiki

| Section | Contents |
|---|---|
| [Quick start](quickstart.md) | Build, install, first script, reload |
| [Syntax and lexing](syntax.md) | Indentation, comments, strings, numbers, variables |
| [Triggers, commands, functions](declarations.md) | `on`, `command`, `function`, events, `where` |
| [Statements](statements.md) | `if`, loops, assignment, `stop`/`return` |
| [Expressions and types](expressions.md) | Precedence, ternary, calls, type inference |
| [Built-in functions](reference-functions.md) | Full catalog with signatures |
| [Players and variables](players.md) | Player properties, `$`/`#`, `variables.yml` |
| [Script examples](examples.md) | Annotated ready-made scripts |
| [Operations](ops.md) | Admin commands, `plugin.yml`, benchmarks, FAQ |

## Requirements

- Server Paper 1.21.8+ / Leaf, compiled against `paper-api 1.21.8-R0.1-SNAPSHOT`.
- Java 21+ (`--release 21`). No Gradle/Maven needed.
