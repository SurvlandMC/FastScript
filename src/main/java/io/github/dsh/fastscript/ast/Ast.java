package io.github.dsh.fastscript.ast;

import java.util.List;

/**
 * Immutable syntax tree for a script file.
 *
 * <p>The tree is shared by both execution strategies: {@code compiler} turns it into JVM
 * bytecode, while {@code bench} walks it directly to provide the interpreted baseline used
 * in performance comparisons.</p>
 */
public final class Ast {

    private Ast() {
    }

    /** Static value kinds tracked by the compiler to keep primitives unboxed. */
    public enum Type {
        VOID,
        NULL,
        NUMBER,
        TEXT,
        BOOL,
        LIST,
        MAP,
        PLAYER,
        EVENT,
        ANY;

        public boolean isNumber() {
            return this == NUMBER;
        }

        public boolean isKnown() {
            return this != ANY;
        }
    }

    /** Source location, used for diagnostics and for mapping generated frames back to script lines. */
    public record Pos(int line, int column) {

        public static final Pos NONE = new Pos(0, 0);
    }

    // ------------------------------------------------------------------ declarations

    public sealed interface Decl permits Trigger, CommandDecl, FunctionDecl, GlobalVarDecl {
        Pos pos();
    }

    /** {@code on <event> [where <condition>]:} */
    public record Trigger(String eventName, String filter, List<Stmt> body, Pos pos) implements Decl {
    }

    /**
     * {@code command <name>:} with an optional inline argument list.
     *
     * @param name        command label without the leading slash
     * @param arguments   argument names in order; each becomes a text argument
     * @param greedyLast  when true the final argument captures the remaining line
     * @param permission  required permission, or {@code null}
     * @param description shown in the help listing
     */
    public record CommandDecl(String name, List<String> arguments, boolean greedyLast,
            String permission, String description, List<Stmt> body, Pos pos) implements Decl {
    }

    /** {@code function <name>(a, b) -> number:} where the return annotation is optional. */
    public record FunctionDecl(String name, List<String> parameters, Type returnType,
            List<Stmt> body, Pos pos) implements Decl {
    }

    /** {@code global $name = 0} — declares a variable and optionally seeds its value. */
    public record GlobalVarDecl(String name, Expr initial, boolean playerScoped, Pos pos) implements Decl {
    }

    // ------------------------------------------------------------------ statements

    public sealed interface Stmt permits Assign, Condition, While, LoopTimes, ForEach, BreakLoop,
            ContinueLoop, Effect, Stop, Return, ExpressionStmt {
        Pos pos();
    }

    public record Assign(Target target, String operator, Expr value, Pos pos) implements Stmt {
    }

    public record Condition(Expr test, List<Stmt> then, List<Stmt> otherwise, Pos pos) implements Stmt {
    }

    public record While(Expr test, List<Stmt> body, Pos pos) implements Stmt {
    }

    public record LoopTimes(Expr count, List<Stmt> body, Pos pos) implements Stmt {
    }

    public record ForEach(String variable, Expr source, List<Stmt> body, Pos pos) implements Stmt {
    }

    public record BreakLoop(Pos pos) implements Stmt {
    }

    public record ContinueLoop(Pos pos) implements Stmt {
    }

    /** A statement written as a bare effect call, for example {@code message "hi"}. */
    public record Effect(String name, List<Expr> arguments, Pos pos) implements Stmt {
    }

    public record Stop(Pos pos) implements Stmt {
    }

    public record Return(Expr value, Pos pos) implements Stmt {
    }

    /** A bare expression evaluated for its side effects. */
    public record ExpressionStmt(Expr expression, Pos pos) implements Stmt {
    }

    // ------------------------------------------------------------------ assignment targets

    public sealed interface Target permits LocalTarget, GlobalTarget, PlayerTarget, IndexTarget,
            PropertyTarget {
        Pos pos();
    }

    public record LocalTarget(String name, Pos pos) implements Target {
    }

    public record GlobalTarget(String name, Pos pos) implements Target {
    }

    public record PlayerTarget(String name, Pos pos) implements Target {
    }

    public record IndexTarget(Expr receiver, Expr key, Pos pos) implements Target {
    }

    /** Assignable player (or list-size) property, for example {@code victim.health}. */
    public record PropertyTarget(Expr receiver, String name, Pos pos) implements Target {
    }

    // ------------------------------------------------------------------ expressions

    public sealed interface Expr permits Literal, Local, Global, PlayerVar, Binary, Unary,
            Call, Property, Index, Ternary {
        Pos pos();
    }

    public record Literal(Object value, Type type, Pos pos) implements Expr {
    }

    public record Local(String name, Pos pos) implements Expr {
    }

    public record Global(String name, Pos pos) implements Expr {
    }

    public record PlayerVar(String name, Pos pos) implements Expr {
    }

    public record Binary(String operator, Expr left, Expr right, Pos pos) implements Expr {
    }

    public record Unary(String operator, Expr operand, Pos pos) implements Expr {
    }

    /** A function call or a builtin expressed as a call, for example {@code random(1, 6)}. */
    public record Call(String name, List<Expr> arguments, Pos pos) implements Expr {
    }

    /** {@code player.health}, {@code player.x} and similar reads. */
    public record Property(Expr receiver, String name, Pos pos) implements Expr {
    }

    public record Index(Expr receiver, Expr key, Pos pos) implements Expr {
    }

    public record Ternary(Expr test, Expr whenTrue, Expr whenFalse, Pos pos) implements Expr {
    }

    // ------------------------------------------------------------------ script

    /**
     * A parsed script file.
     *
     * @param fileName base name of the source file, used in diagnostics and class naming
     * @param position maximum frame slots required by any handler, computed by the compiler
     */
    public record Script(String fileName, List<Decl> declarations, int position) {

        public Script(String fileName, List<Decl> declarations) {
            this(fileName, declarations, 0);
        }

        public List<Trigger> triggers() {
            return declarations.stream().filter(Trigger.class::isInstance).map(Trigger.class::cast).toList();
        }

        public List<CommandDecl> commands() {
            return declarations.stream().filter(CommandDecl.class::isInstance).map(CommandDecl.class::cast).toList();
        }

        public List<FunctionDecl> functions() {
            return declarations.stream().filter(FunctionDecl.class::isInstance).map(FunctionDecl.class::cast).toList();
        }
    }
}
