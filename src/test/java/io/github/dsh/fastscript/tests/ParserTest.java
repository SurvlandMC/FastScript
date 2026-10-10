package io.github.dsh.fastscript.tests;

import io.github.dsh.fastscript.ast.Ast;
import io.github.dsh.fastscript.core.ScriptException;
import io.github.dsh.fastscript.lang.Parser;
import java.util.List;

/** Parser tests: precedence, declarations, statements, diagnostics. */
public final class ParserTest {

    private ParserTest() {
    }

    static Ast.Expr expr(String source) {
        return Parser.parseExpressionOnly(source, "test.fs");
    }

    static Ast.Script script(String source) {
        return Parser.parse(source, "test.fs");
    }

    public static void run(Suite s) {
        // --- precedence -----------------------------------------------------
        Ast.Expr addMul = expr("1 + 2 * 3");
        s.check(addMul instanceof Ast.Binary add
                        && add.operator().equals("+")
                        && add.left() instanceof Ast.Literal
                        && add.right() instanceof Ast.Binary mul
                        && mul.operator().equals("*"),
                "parser.precedence-add-mul");
        Ast.Expr orAnd = expr("a or b and c");
        s.check(orAnd instanceof Ast.Binary or
                        && or.operator().equals("or")
                        && or.right() instanceof Ast.Binary and
                        && and.operator().equals("and"),
                "parser.precedence-or-and");
        // `not` binds tighter than comparison: `(not a) == b`.
        Ast.Expr notEq = expr("not a == b");
        s.check(notEq instanceof Ast.Binary eq
                        && eq.operator().equals("==")
                        && eq.left() instanceof Ast.Unary not
                        && not.operator().equals("!"),
                "parser.not-binds-tighter-than-eq");
        s.check(expr("-a + b") instanceof Ast.Binary, "parser.unary-minus");
        s.check(expr("a ? b : c") instanceof Ast.Ternary, "parser.ternary");
        s.check(expr("x is null") instanceof Ast.Binary is
                        && is.operator().equals("is"),
                "parser.is-operator");
        s.check(expr("a contains b") instanceof Ast.Binary contains
                        && contains.operator().equals("contains"),
                "parser.contains-operator");
        s.check(expr("sender().health") instanceof Ast.Property, "parser.property");
        s.check(expr("a[0]") instanceof Ast.Index, "parser.index");
        Ast.Expr call = expr("f(1, \"x\")");
        s.check(call instanceof Ast.Call f
                        && f.name().equals("f")
                        && f.arguments().size() == 2,
                "parser.call-args");
        s.check(expr("true") instanceof Ast.Literal t
                        && Boolean.TRUE.equals(t.value())
                        && t.type() == Ast.Type.BOOL,
                "parser.bool-literal");
        s.check(expr("null") instanceof Ast.Literal n
                        && n.value() == null
                        && n.type() == Ast.Type.NULL,
                "parser.null-literal");

        // --- declarations ---------------------------------------------------
        Ast.Script trigger = script("on player join:\n    stop\n");
        s.check(trigger.triggers().size() == 1
                        && trigger.triggers().get(0).eventName().equals("player join"),
                "parser.trigger-name");
        Ast.Script filtered = script("on join where health < 20:\n    stop\n");
        s.check("health < 20".equals(filtered.triggers().get(0).filter()),
                "parser.where-filter-text");
        Ast.Script command = script("command heal(target : text) permission fastscript.heal:\n    stop\n");
        Ast.CommandDecl heal = command.commands().get(0);
        s.check(heal.name().equals("heal")
                        && heal.arguments().equals(List.of("target"))
                        && heal.greedyLast()
                        && "fastscript.heal".equals(heal.permission()),
                "parser.command-full");
        Ast.CommandDecl kit = script("command kit:\n    stop\n").commands().get(0);
        s.check(kit.arguments().isEmpty() && kit.permission() == null,
                "parser.command-bare");
        Ast.FunctionDecl triangle = script("function triangle(n) : number:\n    return n\n")
                .functions().get(0);
        s.check(triangle.parameters().equals(List.of("n"))
                        && triangle.returnType() == Ast.Type.NUMBER,
                "parser.function-typed");
        s.check(script("function f():\n    stop\n").functions().get(0).returnType()
                        == Ast.Type.VOID,
                "parser.function-void");
        s.check(script("$g = 0\n").declarations().get(0) instanceof Ast.GlobalVarDecl,
                "parser.global-decl");

        // --- statements -----------------------------------------------------
        List<Ast.Stmt> body = script("function f():\n    let x = 1\n    x += 2\n    $g = 3\n    #p = 4\n")
                .functions().get(0).body();
        s.check(body.get(0) instanceof Ast.Assign let
                        && let.target() instanceof Ast.LocalTarget
                        && let.operator().equals("="),
                "parser.let-assign");
        s.check(body.get(1) instanceof Ast.Assign plus
                        && plus.operator().equals("+="),
                "parser.compound-assign");
        s.check(body.get(2) instanceof Ast.Assign global
                        && global.target() instanceof Ast.GlobalTarget,
                "parser.global-target");
        s.check(body.get(3) instanceof Ast.Assign player
                        && player.target() instanceof Ast.PlayerTarget,
                "parser.player-target");

        List<Ast.Stmt> flow = script("function f():\n    if a:\n        stop\n    else:\n        stop\n")
                .functions().get(0).body();
        s.check(flow.get(0) instanceof Ast.Condition cond
                        && cond.then().size() == 1
                        && cond.otherwise().size() == 1,
                "parser.if-else");
        s.check(script("function f():\n    while a:\n        stop\n").functions().get(0)
                        .body().get(0) instanceof Ast.While,
                "parser.while");
        Ast.Stmt loop = script("function f():\n    loop 3 times:\n        stop\n")
                .functions().get(0).body().get(0);
        s.check(loop instanceof Ast.LoopTimes times
                        && times.count() instanceof Ast.Literal,
                "parser.loop-times");
        Ast.Stmt forEach = script("function f():\n    for x in items:\n        stop\n")
                .functions().get(0).body().get(0);
        s.check(forEach instanceof Ast.ForEach each
                        && each.variable().equals("x"),
                "parser.for-in");
        List<Ast.Stmt> jumps = script("function f():\n    break\n    continue\n    stop\n    return\n")
                .functions().get(0).body();
        s.check(jumps.get(0) instanceof Ast.BreakLoop
                        && jumps.get(1) instanceof Ast.ContinueLoop
                        && jumps.get(2) instanceof Ast.Stop
                        && jumps.get(3) instanceof Ast.Return ret
                        && ret.value() == null,
                "parser.jumps-stop-bare-return");

        Ast.Stmt bare = script("function f():\n    message \"hi\"\n")
                .functions().get(0).body().get(0);
        s.check(bare instanceof Ast.Effect effect
                        && effect.name().equals("message")
                        && effect.arguments().size() == 1,
                "parser.bare-effect");
        Ast.Stmt give = script("function f():\n    give \"DIAMOND\" 3\n")
                .functions().get(0).body().get(0);
        s.check(give instanceof Ast.Effect g && g.arguments().size() == 2,
                "parser.bare-effect-args");

        // --- diagnostics ----------------------------------------------------
        s.failsMsg("unknown type 'dragon'",
                () -> script("function f() : dragon:\n    stop\n"), "parser.unknown-type");
        s.failsMsg("expected '=' after the declared variable name",
                () -> script("function f():\n    let x\n"), "parser.let-missing-eq");
        s.failsMsg("expected 'in' after the loop variable",
                () -> script("function f():\n    for x y:\n        stop\n"), "parser.for-missing-in");
        s.fails(ScriptException.class,
                () -> script("function f():\n"), "parser.empty-block");
        s.fails(ScriptException.class,
                () -> script("hello\n"), "parser.bad-toplevel");
    }
}
