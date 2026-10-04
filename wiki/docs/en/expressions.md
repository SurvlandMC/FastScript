# Expressions and types

## Operator precedence (lowest first)

1. Ternary `cond ? a : b`
2. `or`, `||`
3. `and`, `&&`
4. Comparisons `== != < > <= >= contains is` (`is` = `==`)
5. Addition `+ -` and concatenation `<<< >>>`
6. Multiplication `* / %` (division by zero is an error, not `NaN`)
7. Unary `- ! not ++ --`
8. Postfix: `.property`, `[key]`, `x++` / `x--`
9. Primary: literals, variables, `f(...)`, `( ... )`

`+` is both addition and concatenation: if either side is text, the result
is text (`"n: " + 5` → `"n: 5"`). `and`/`or` short-circuit.

## Comparisons and membership

```fs
if n < 2:
    return n
if box contains "sword":
    message "Armed!"
if victim is null:
    stop
```

`contains` works for lists (via `looseEquals`), maps (key lookup) and text
(substring). Numbers compare numerically, everything else as text.

## Calls, properties, indexes

```fs
let victim = player(target)   # function/builtin call
message victim.name           # player property
message box[0]                # list/map/string index
```

They chain: `player(target).health`, `args[0]`.
An unknown function is a compile error: `unknown function '...'`.

## Type coercion

Values are plain JDK types (`Double`, `String`, `Boolean`, `List`, `Map`),
coerced when needed:

- to number: `Boolean` → 1/0, text gets parsed (empty/garbage is an error);
- to boolean: `Boolean` as-is, number is `!= 0`, text is non-empty and not `"false"`;
- to text: `null` → `""`, integral doubles without `.0`
  (`5`, not `5.0`), lists as `[a, b]`.

## How type inference works (for the curious)

`Compiler.kindOf` trusts only reliable leaves: literals, locals with
a consistent `Storage.kind`, calls with known signatures.

- `number` + `number` → primitive `DADD`/`DCMPL`, no boxing;
- `text` + `text` in comparisons → `String.equals`/`concat`;
- the rest is boxed **immediately after its operand is computed** and goes
  to `Values.*` helpers;
- `+` with an unknown operand yields kind `ANY` (not `NUMBER`), otherwise
  chains like `a + b + "x"` would lose their text-ness;
- boxing happens in exactly one place: re-boxing an already-object value
  is forbidden (the actually produced representation is checked),
  otherwise you get a `VerifyError`;
- generated constructors use `INVOKESPECIAL` only.
