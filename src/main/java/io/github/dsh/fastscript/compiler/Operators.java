package io.github.dsh.fastscript.compiler;

import io.github.dsh.fastscript.ast.Ast;
import java.util.Locale;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

/**
 * Maps script operators to either inlined JVM instructions or helper calls.
 *
 * <p>This is the main reason generated code is fast: {@code +}, {@code <} and friends become
 * primitive instructions whenever the compiler could prove both sides are numbers, and only
 * fall back to a typed helper when a value is dynamic.</p>
 */
public final class Operators {

    /** JVM internal name of the runtime helpers generated code calls into. */
    static final String VALUES_OWNER = Code.VALUES_OWNER;

    private Operators() {
    }

    /**
     * The unboxed representation an expression leaves on the stack.
     *
     * @param jvmType JVM descriptor of the value on the operand stack
     * @param astType script-level type used for further inference
     */
    public record Rep(String jvmType, Ast.Type astType) {

        public static final Rep NUMBER = new Rep("D", Ast.Type.NUMBER);
        public static final Rep BOOL = new Rep("Z", Ast.Type.BOOL);
        public static final Rep TEXT = new Rep("Ljava/lang/String;", Ast.Type.TEXT);
        public static final Rep OBJECT = new Rep("Ljava/lang/Object;", Ast.Type.ANY);
        public static final Rep VOID = new Rep("V", Ast.Type.VOID);

        public boolean isNumber() {
            return jvmType.equals("D");
        }

        public boolean isObject() {
            return jvmType.equals(Code.OBJECT);
        }

        public boolean isText() {
            return jvmType.equals(Code.STRING);
        }

        public boolean isBool() {
            return jvmType.equals("Z");
        }

        public boolean isVoid() {
            return jvmType.equals("V");
        }

        /** Best-effort script kind of this representation, used for type inference. */
        public Ast.Type kind() {
            return switch (jvmType) {
                case Code.DOUBLE -> Ast.Type.NUMBER;
                case Code.BOOL -> Ast.Type.BOOL;
                case Code.STRING -> Ast.Type.TEXT;
                default -> Ast.Type.ANY;
            };
        }
    }

    public static Rep repOf(Ast.Type type) {
        return switch (type) {
            case NUMBER -> Rep.NUMBER;
            case TEXT -> Rep.TEXT;
            case BOOL -> Rep.BOOL;
            case VOID -> Rep.VOID;
            default -> Rep.OBJECT;
        };
    }

    // ------------------------------------------------------------------ conversion

    /**
     * Converts the top of the stack from {@code from} to {@code to}.
     *
     * <p>Converting <em>to</em> {@code Object} is deliberately a no-op: any value that already
     * lives in a JVM slot is a reference. Callers that hold a primitive and need an object must
     * say so with {@link #boxToObject}.</p>
     */
    public static void convert(Code code, Rep from, Rep to) {
        if (from.jvmType().equals(to.jvmType())) {
            return;
        }
        if (to.isObject()) {
            return;
        }
        if (from.isObject()) {
            code.unbox(to.jvmType());
            return;
        }
        if (from.isNumber() && to.isText()) {
            code.invokeStatic(VALUES_OWNER, "format", "(D)Ljava/lang/String;");
            return;
        }
        if (from.isBool() && to.isNumber()) {
            code.i2d();
            return;
        }
        if (from.isNumber() && to.isBool()) {
            // true when the double differs from zero
            code.dconst(0);
            code.dcmpl();
            branchToBoolean(code, Opcodes.IFNE);
            return;
        }
        if (from.isText() && to.isNumber()) {
            code.invokeStatic(VALUES_OWNER, "toNumber", "(Ljava/lang/Object;)D");
            return;
        }
        if (from.isText() && to.isBool()) {
            code.invokeStatic(VALUES_OWNER, "toBool", "(Ljava/lang/Object;)Z");
            return;
        }
        if (from.isBool() && to.isText()) {
            code.box(from.jvmType());
            code.invokeStatic(VALUES_OWNER, "toText", "(Ljava/lang/Object;)Ljava/lang/String;");
            return;
        }
        throw new IllegalStateException("cannot convert " + from.jvmType() + " to " + to.jvmType());
    }

    /** Boxes a primitive so it can be handed to reference-typed code. */
    public static void boxToObject(Code code, Rep from) {
        if (!from.isObject()) {
            code.box(from.jvmType());
        }
    }

    // ------------------------------------------------------------------ binary operators

    /** Emits a binary operation given both operand representations already on the stack. */
    public static void binary(Code code, String operator, Rep left, Rep right) {
        switch (operator.toLowerCase(Locale.ROOT)) {
            case "+" -> {
                if (left.isNumber() && right.isNumber()) {
                    code.dadd();
                } else if (left.isText() && right.isText()) {
                    code.invokeVirtual("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;");
                } else {
                    callHelper(code, "add", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            left, right);
                }
            }
            case "-" -> arithmetic(code, "subtract", left, right, code1 -> code1.dsub());
            case "*" -> arithmetic(code, "multiply", left, right, code1 -> code1.dmul());
            case "/" -> {
                if (left.isNumber() && right.isNumber()) {
                    code.ddiv();
                } else {
                    callHelper(code, "divide", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            left, right);
                }
            }
            case "%" -> {
                if (left.isNumber() && right.isNumber()) {
                    code.drem();
                } else {
                    callHelper(code, "modulo", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            left, right);
                }
            }
            case "==", "is" -> equality(code, left, right);
            case "!=" -> inequality(code, left, right);
            case "<" -> ordered(code, "<", left, right);
            case ">" -> ordered(code, ">", left, right);
            case "<=" -> ordered(code, "<=", left, right);
            case ">=" -> ordered(code, ">=", left, right);
            case "contains" -> callHelper(code, "contains", "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                    left, right);
            // Both shift-like operators are explicit text concatenation, even for numbers.
            case "<<<", ">>>" -> callHelper(code, "concat",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", left, right);
            case "and" -> logical(code, left, right, true);
            case "or" -> logical(code, left, right, false);
            default -> throw new IllegalArgumentException("unsupported operator '" + operator + "'");
        }
    }

    /** A single instruction emitted on the operand stack. */
    @FunctionalInterface
    private interface Instruction {
        void emit(Code code);
    }

    private static void arithmetic(Code code, String helper, Rep left, Rep right, Instruction instruction) {
        if (left.isNumber() && right.isNumber()) {
            instruction.emit(code);
            return;
        }
        callHelper(code, helper, "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", left, right);
    }

    /** Emits {@code left <op> right} for an ordering operator. */
    public static void ordered(Code code, String operator, Rep left, Rep right) {
        String helper = switch (operator) {
            case "<" -> "less";
            case ">" -> "greater";
            case "<=" -> "lessOrEqual";
            default -> "greaterOrEqual";
        };
        if (left.isNumber() && right.isNumber()) {
            // DCMPG normalises the result to -1/0/1 and the jump leads to the `true`
            // branch. NaN sorts to +1 here, exactly like Double.compare, which is what
            // the dynamic helpers use — so both paths agree on NaN on every operator.
            code.dcmpg();
            int opcode = switch (operator) {
                case "<" -> Opcodes.IFLT;
                case ">" -> Opcodes.IFGT;
                case "<=" -> Opcodes.IFLE;
                default -> Opcodes.IFGE;
            };
            branchToBoolean(code, opcode);
            return;
        }
        // Operands of differing kinds are boxed before the helper sees them.
        callHelper(code, helper, "(Ljava/lang/Object;Ljava/lang/Object;)Z", left, right);
    }

    /** Emits inequality by negating equality. */
    public static void inequality(Code code, Rep left, Rep right) {
        equality(code, left, right);
        code.iconst(1);
        code.ixor();
    }

    /** Turns a conditional branch into a boolean value on the stack. */
    private static void branchToBoolean(Code code, int jumpOpcode) {
        Label whenTrue = new Label();
        Label done = new Label();
        code.jump(jumpOpcode, whenTrue);
        code.iconst(0);
        code.jump(Opcodes.GOTO, done);
        code.label(whenTrue);
        code.iconst(1);
        code.label(done);
    }

    /** Emits {@code left == right}. */
    public static void equality(Code code, Rep left, Rep right) {
        if (left.isNumber() && right.isNumber()) {
            code.dcmpl();
            branchToBoolean(code, Opcodes.IFEQ);
            return;
        }
        if (left.isBool() && right.isBool()) {
            Label whenTrue = new Label();
            Label done = new Label();
            code.jump(Opcodes.IF_ICMPEQ, whenTrue);
            code.iconst(0);
            code.jump(Opcodes.GOTO, done);
            code.label(whenTrue);
            code.iconst(1);
            code.label(done);
            return;
        }
        if (left.isText() && right.isText()) {
            code.invokeVirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
            return;
        }
        callHelper(code, "looseEquals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", left, right);
    }

    private static void logical(Code code, Rep left, Rep right, boolean and) {
        // Short-circuiting needs jumps, so the compiler emits that form; this path covers the
        // case where both operands are already on the stack.
        Label done = new Label();
        convert(code, left, Rep.BOOL);
        if (and) {
            Label whenFalse = new Label();
            code.jump(Opcodes.IFEQ, whenFalse);
            convert(code, right, Rep.BOOL);
            code.jump(Opcodes.GOTO, done);
            code.label(whenFalse);
            code.iconst(0);
        } else {
            Label whenTrue = new Label();
            code.jump(Opcodes.IFNE, whenTrue);
            convert(code, right, Rep.BOOL);
            code.jump(Opcodes.GOTO, done);
            code.label(whenTrue);
            code.iconst(1);
        }
        code.label(done);
    }

    private static void callHelper(Code code, String name, String descriptor, Rep left, Rep right) {
        code.box(left.jvmType());
        code.box(right.jvmType());
        code.invokeStatic(VALUES_OWNER, name, descriptor);
    }

    /** Result type of a binary operation, used for further inference. */
    public static Ast.Type resultType(String operator, Ast.Type left, Ast.Type right, boolean stringHint) {
        return switch (operator.toLowerCase(Locale.ROOT)) {
            case "+" -> {
                if (left == Ast.Type.TEXT || right == Ast.Type.TEXT || stringHint) {
                    yield Ast.Type.TEXT;
                }
                // A mixed or unknown operand may still concatenate at run time, so the result
                // kind stays unknown rather than being narrowed to a number.
                yield left == Ast.Type.NUMBER && right == Ast.Type.NUMBER
                        ? Ast.Type.NUMBER
                        : Ast.Type.ANY;
            }
            case "-", "*", "/", "%" -> Ast.Type.NUMBER;
            case "<<<", ">>>" -> Ast.Type.TEXT;
            case "==", "!=", "<", ">", "<=", ">=", "contains", "is", "and", "or" -> Ast.Type.BOOL;
            default -> Ast.Type.ANY;
        };
    }

    /** Script-level operator table used by diagnostics and documentation. */
    public static Map<String, String> summary() {
        return Map.ofEntries(
                Map.entry("+", "addition or text concatenation"),
                Map.entry("-", "subtraction"),
                Map.entry("*", "multiplication"),
                Map.entry("/", "division"),
                Map.entry("%", "remainder"),
                Map.entry("==", "equality"),
                Map.entry("!=", "inequality"),
                Map.entry("<", "less than"),
                Map.entry(">", "greater than"),
                Map.entry("<=", "less or equal"),
                Map.entry(">=", "greater or equal"),
                Map.entry("contains", "membership test"),
                Map.entry("and", "logical conjunction"),
                Map.entry("or", "logical disjunction"));
    }
}
