package io.github.dsh.fastscript.compiler;

import io.github.dsh.fastscript.ast.Ast;
import io.github.dsh.fastscript.ast.Ast.Assign;
import io.github.dsh.fastscript.ast.Ast.Binary;
import io.github.dsh.fastscript.ast.Ast.BreakLoop;
import io.github.dsh.fastscript.ast.Ast.Call;
import io.github.dsh.fastscript.ast.Ast.CommandDecl;
import io.github.dsh.fastscript.ast.Ast.Condition;
import io.github.dsh.fastscript.ast.Ast.ContinueLoop;
import io.github.dsh.fastscript.ast.Ast.Decl;
import io.github.dsh.fastscript.ast.Ast.Effect;
import io.github.dsh.fastscript.ast.Ast.Expr;
import io.github.dsh.fastscript.ast.Ast.ForEach;
import io.github.dsh.fastscript.ast.Ast.FunctionDecl;
import io.github.dsh.fastscript.ast.Ast.Global;
import io.github.dsh.fastscript.ast.Ast.GlobalTarget;
import io.github.dsh.fastscript.ast.Ast.GlobalVarDecl;
import io.github.dsh.fastscript.ast.Ast.Index;
import io.github.dsh.fastscript.ast.Ast.IndexTarget;
import io.github.dsh.fastscript.ast.Ast.Literal;
import io.github.dsh.fastscript.ast.Ast.Local;
import io.github.dsh.fastscript.ast.Ast.LocalTarget;
import io.github.dsh.fastscript.ast.Ast.LoopTimes;
import io.github.dsh.fastscript.ast.Ast.PlayerTarget;
import io.github.dsh.fastscript.ast.Ast.PlayerVar;
import io.github.dsh.fastscript.ast.Ast.Property;
import io.github.dsh.fastscript.ast.Ast.Return;
import io.github.dsh.fastscript.ast.Ast.Script;
import io.github.dsh.fastscript.ast.Ast.Stop;
import io.github.dsh.fastscript.ast.Ast.Stmt;
import io.github.dsh.fastscript.ast.Ast.Target;
import io.github.dsh.fastscript.ast.Ast.Ternary;
import io.github.dsh.fastscript.ast.Ast.Trigger;
import io.github.dsh.fastscript.ast.Ast.Unary;
import io.github.dsh.fastscript.ast.Ast.While;
import io.github.dsh.fastscript.core.ScriptException;
import io.github.dsh.fastscript.lang.Parser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Compiles a parsed {@link Script} into a JVM class.
 *
 * <p>Every trigger, command and function becomes a static method, so control flow is native
 * bytecode: conditions compile to conditional jumps, loops to backward jumps, arithmetic to
 * primitive instructions. Only genuinely dynamic operations вЂ” list indexing, player
 * properties, non-inlinable builtins вЂ” call into runtime helpers.</p>
 *
 * <h2>Value storage</h2>
 * <ul>
 *   <li><b>JVM locals</b> hold script variables created by assignment; they always store
 *       references, which keeps one slot per variable regardless of the value kind.</li>
 *   <li><b>Execution-context slots</b> (a flat {@code Object[]} sized at compile time) hold
 *       loop counters and iterators that must survive nested calls.</li>
 *   <li><b>Command arguments</b> are read straight from the invocation array.</li>
 * </ul>
 */
public final class Compiler {

    private static final String CONTEXT_OWNER = Builtins.CONTEXT_OWNER;
    private static final String HANDLE_OWNER = "io/github/dsh/fastscript/runtime/ScriptHandle";
    private static final String HANDLE_DESC = "L" + HANDLE_OWNER + ";";
    private static final String TRIGGER_DESC = "Lio/github/dsh/fastscript/runtime/TriggerSpec;";
    private static final String COMMAND_DESC = "Lio/github/dsh/fastscript/runtime/CommandSpec;";
    private static final String FUNCTION_DESC = "Lio/github/dsh/fastscript/runtime/FunctionSpec;";
    private static final String LIST_OWNER = "java/util/ArrayList";
    private static final String OBJECT_DESC = Code.OBJECT;
    private static final String STRING_DESC = Code.STRING;
    private static final String ARGS_DESC = "[Ljava/lang/Object;";
    private static final String CONTEXT_DESC = "L" + CONTEXT_OWNER + ";";
    private static final String HANDLER_DESC = "(" + ARGS_DESC + CONTEXT_DESC + ")V";

    private final Script script;
    private final String className;
    private final String scriptId;
    private final String sourceName;
    private final Map<String, FunctionSignature> functions = new LinkedHashMap<>();
    private final List<HandlerInfo> handlers = new ArrayList<>();

    /** Static description of a script function, known before any body is compiled. */
    private record FunctionSignature(String name, List<String> parameters, Ast.Type returnType,
            String methodName, String descriptor) {
    }

    /** A handler this script exposes, recorded so the generated factory can register it. */
    private record HandlerInfo(String kind, String name, String methodName, String metadata,
            List<String> arguments) {
    }

    /** Which kind of handler a statement list belongs to, for diagnostics. */
    private enum Body {
        TRIGGER,
        COMMAND
    }

    public Compiler(Script script, String className, String scriptId, String sourceName) {
        this.script = script;
        this.className = className;
        this.scriptId = scriptId;
        this.sourceName = sourceName;
    }

    /** Compiles the script and returns the generated class bytes. */
    public byte[] compile() {
        collectDeclarations();

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                className, null, HANDLE_OWNER, null);
        writer.visitSource(sourceName, null);

        emitConstructor(writer);

        // Bodies first: they decide the frame-slot budget that the factory embeds.
        List<Runnable> bodies = new ArrayList<>();
        for (Decl declaration : script.declarations()) {
            switch (declaration) {
                case Trigger trigger -> bodies.add(() -> emitTrigger(writer, trigger));
                case CommandDecl command -> bodies.add(() -> emitCommand(writer, command));
                case FunctionDecl function -> bodies.add(() -> emitFunction(writer, function));
                case GlobalVarDecl ignored -> {
                    // Declarations only guarantee the variable exists in the store.
                }
            }
        }
        for (Runnable body : bodies) {
            body.run();
        }
        emitFactory(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** Names of globals declared by this script, used for persistence. */
    public Set<String> declaredGlobals() {
        Set<String> names = new LinkedHashSet<>();
        for (Decl declaration : script.declarations()) {
            if (declaration instanceof GlobalVarDecl global && !global.playerScoped()) {
                names.add(global.name());
            }
        }
        return names;
    }

    // ------------------------------------------------------------------ declarations

    private void collectDeclarations() {
        Set<String> seenCommands = new LinkedHashSet<>();
        Set<String> seenTriggers = new LinkedHashSet<>();
        for (Decl declaration : script.declarations()) {
            switch (declaration) {
                case FunctionDecl function -> {
                    if (functions.containsKey(function.name())) {
                        throw error(function.pos(), "function '" + function.name() + "' is declared twice");
                    }
                    StringBuilder descriptor = new StringBuilder("(");
                    for (int i = 0; i < function.parameters().size(); i++) {
                        descriptor.append(OBJECT_DESC);
                    }
                    descriptor.append(')');
                    descriptor.append(switch (function.returnType()) {
                        case NUMBER -> "D";
                        case TEXT -> STRING_DESC;
                        case BOOL -> "Z";
                        default -> OBJECT_DESC;
                    });
                    functions.put(function.name(), new FunctionSignature(function.name(),
                            List.copyOf(function.parameters()), function.returnType(),
                            "fn$" + sanitize(function.name()), descriptor.toString()));
                }
                case CommandDecl command -> {
                    if (!seenCommands.add(command.name())) {
                        throw error(command.pos(), "command '/" + command.name() + "' is declared twice");
                    }
                    handlers.add(new HandlerInfo("command", command.name(),
                            "command$" + sanitize(command.name()),
                            command.permission() == null ? "" : command.permission(),
                            List.copyOf(command.arguments())));
                }
                case Trigger trigger -> {
                    String methodName = "trigger$" + sanitize(trigger.eventName());
                    if (!seenTriggers.add(methodName)) {
                        throw error(trigger.pos(), "duplicate trigger for event '" + trigger.eventName() + "'");
                    }
                    handlers.add(new HandlerInfo("trigger", trigger.eventName(), methodName,
                            trigger.filter() == null ? "" : trigger.filter(), List.of()));
                }
                case GlobalVarDecl ignored -> {
                    // Nothing to precompute.
                }
            }
        }
    }

    private void emitConstructor(ClassWriter writer) {
        MethodVisitor plain = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        plain.visitCode();
        Code code = new Code(plain);
        code.loadRef(0);
        code.aconst(scriptId);
        code.aconst(sourceName);
        code.invokeSpecial(HANDLE_OWNER, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
        code.returnValue("V");
        plain.visitMaxs(0, 0);
        plain.visitEnd();
    }

    /** Emits the static factory that builds the handle and registers every handler. */
    private void emitFactory(ClassWriter writer) {
        MethodVisitor plain = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "create", "()" + HANDLE_DESC, null, null);
        plain.visitCode();
        Code code = new Code(plain);

        code.newInstance(className);
        code.dup();
        code.invokeSpecial(className, "<init>", "()V");
        code.storeRef(0);

        for (HandlerInfo handler : handlers) {
            code.loadRef(0);
            if (handler.kind().equals("trigger")) {
                code.aconst(handler.name());
                code.aconst(handler.metadata());
                code.aconst(handler.methodName());
                code.invokeStatic("io/github/dsh/fastscript/runtime/TriggerSpec", "of",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)" + TRIGGER_DESC);
                code.invokeVirtual(HANDLE_OWNER, "addTrigger", "(" + TRIGGER_DESC + ")V");
            } else {
                code.aconst(handler.name());
                code.aconst(handler.metadata());
                code.aconst(handler.methodName());
                code.aconst(String.join(",", handler.arguments()));
                code.invokeStatic("io/github/dsh/fastscript/runtime/CommandSpec", "of",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)"
                                + COMMAND_DESC);
                code.invokeVirtual(HANDLE_OWNER, "addCommand", "(" + COMMAND_DESC + ")V");
            }
        }

        for (FunctionSignature signature : functions.values()) {
            code.loadRef(0);
            code.aconst(signature.name());
            code.aconst(signature.methodName());
            code.aconst(signature.descriptor());
            code.iconst(signature.parameters().size());
            code.invokeStatic("io/github/dsh/fastscript/runtime/FunctionSpec", "of",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)" + FUNCTION_DESC);
            code.invokeVirtual(HANDLE_OWNER, "addFunction", "(" + FUNCTION_DESC + ")V");
        }

        code.loadRef(0);
        code.returnValue(OBJECT_DESC);
        plain.visitMaxs(0, 0);
        plain.visitEnd();
    }

    private void emitTrigger(ClassWriter writer, Trigger trigger) {
        MethodVisitor plain = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "trigger$" + sanitize(trigger.eventName()), HANDLER_DESC, null, null);
        MethodState state = begin(plain, 2);
        state.hasContext = true;
        if (trigger.filter() != null && !trigger.filter().isBlank()) {
            compileFilter(state, trigger.filter(), trigger.pos());
        }
        compileBody(state, trigger.body(), Body.TRIGGER, Map.of());
        state.code.returnValue("V");
        finish(state);
    }

    /**
     * Compiles a {@code where} filter into a guard: a false condition returns immediately, so
     * filters never reach the statement compiler.
     */
    private void compileFilter(MethodState state, String filter, Ast.Pos pos) {
        Expr condition = Parser.parseExpressionOnly(filter, sourceName);
        Label proceed = new Label();
        pushBoolean(state, new Unary("!", condition, pos));
        state.code.jump(Opcodes.IFEQ, proceed);
        state.code.returnValue("V");
        state.code.label(proceed);
    }

    private void emitCommand(ClassWriter writer, CommandDecl command) {
        MethodVisitor plain = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "command$" + sanitize(command.name()), HANDLER_DESC, null, null);
        MethodState state = begin(plain, 2);
        state.hasContext = true;
        Map<String, Storage> arguments = new LinkedHashMap<>();
        for (int index = 0; index < command.arguments().size(); index++) {
            // Command arguments live at args[2 + index]: args[0] is the sender, args[1] the event.
            arguments.put(command.arguments().get(index),
                    new Storage.Argument(index + 2, Ast.Type.TEXT));
        }
        compileBody(state, command.body(), Body.COMMAND, arguments);
        state.code.returnValue("V");
        finish(state);
    }

    private void emitFunction(ClassWriter writer, FunctionDecl function) {
        FunctionSignature signature = functions.get(function.name());
        MethodVisitor plain = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                signature.methodName(), signature.descriptor(), null, null);
        Map<String, Storage> parameters = new LinkedHashMap<>();
        int slot = 0;
        for (String parameter : function.parameters()) {
            parameters.put(parameter, new Storage.JvmLocal(slot, Ast.Type.ANY));
            slot++;
        }
        MethodState state = begin(plain, slot);
        state.returnType = signature.returnType();
        compileBody(state, function.body(), Body.TRIGGER, parameters);
        switch (signature.returnType()) {
            case NUMBER -> {
                state.code.dconst(0);
                state.code.returnValue(Code.DOUBLE);
            }
            case BOOL -> {
                state.code.iconst(0);
                state.code.returnValue(Code.BOOL);
            }
            case VOID -> state.code.returnValue("V");
            default -> {
                state.code.aconst(null);
                state.code.returnValue(OBJECT_DESC);
            }
        }
        finish(state);
    }

    // ------------------------------------------------------------------ statements

    private void compileBody(MethodState state, List<Stmt> statements, Body body,
            Map<String, Storage> initialScope) {
        state.scopes.push(new LinkedHashMap<>(initialScope));
        for (Stmt statement : statements) {
            compileStatement(state, statement, body);
        }
        state.scopes.pop();
    }

    /** Compiles a nested block; locals declared inside are released at the end. */
    private void compileScoped(MethodState state, List<Stmt> statements, Body body) {
        int savedNext = state.nextLocal;
        state.scopes.push(new LinkedHashMap<>());
        for (Stmt statement : statements) {
            compileStatement(state, statement, body);
        }
        state.scopes.pop();
        state.nextLocal = savedNext;
    }

    private void compileStatement(MethodState state, Stmt statement, Body body) {
        if (statement.pos().line() > 0) {
            state.code.lineNumber(statement.pos().line(), new Label());
        }
        switch (statement) {
            case Assign assign -> compileAssign(state, assign);
            case Condition condition -> {
                Label otherwise = new Label();
                Label done = new Label();
                pushBoolean(state, condition.test());
                state.code.jump(Opcodes.IFEQ, otherwise);
                compileScoped(state, condition.then(), body);
                if (!condition.otherwise().isEmpty()) {
                    state.code.jump(Opcodes.GOTO, done);
                    state.code.label(otherwise);
                    compileScoped(state, condition.otherwise(), body);
                    state.code.label(done);
                } else {
                    state.code.label(otherwise);
                    state.code.label(done);
                }
            }
            case While loop -> {
                Label test = new Label();
                Label bodyLabel = new Label();
                Label done = new Label();
                state.code.label(test);
                pushBoolean(state, loop.test());
                state.code.jump(Opcodes.IFNE, bodyLabel);
                state.code.jump(Opcodes.GOTO, done);
                state.code.label(bodyLabel);
                state.loops.push(new LoopLabels(test, done));
                compileScoped(state, loop.body(), body);
                state.loops.pop();
                state.code.jump(Opcodes.GOTO, test);
                state.code.label(done);
            }
            case LoopTimes loop -> compileLoopTimes(state, loop, body);            case ForEach loop -> compileForEach(state, loop, body);
            case Effect effect -> compileEffect(state, effect.name(), effect.arguments(), effect.pos());
            case Stop ignored -> state.code.returnValue("V");
            case BreakLoop ignored -> {
                requireLoop(state, statement.pos(), "break");
                state.code.jump(Opcodes.GOTO, state.loops.peek().breakTarget());
            }
            case ContinueLoop ignored -> {
                requireLoop(state, statement.pos(), "continue");
                state.code.jump(Opcodes.GOTO, state.loops.peek().continueTarget());
            }
            case Return returned -> compileReturn(state, returned);
            case Ast.ExpressionStmt expression ->
                    discard(state, pushExpression(state, expression.expression(), Operators.Rep.OBJECT));
        }
    }

    private void requireLoop(MethodState state, Ast.Pos pos, String keyword) {
        if (state.loops.isEmpty()) {
            throw error(pos, "'" + keyword + "' outside of a loop");
        }
    }

    private void compileAssign(MethodState state, Assign assign) {
        Target target = assign.target();
        if (assign.operator().equals("=")) {
            // The declared kind comes from the expression, not from the (already boxed) result.
            Ast.Type observed = kindOf(state, assign.value());
            if (System.getenv("FASTSCRIPT_TRACE") != null) {
                System.err.println("  SIMPLE " + target + " observed=" + observed);
            }
            writeValue(state, target, pushReference(state, assign.value()), observed);
            return;
        }
        if (System.getenv("FASTSCRIPT_TRACE") != null) {
            System.err.println("  COMPOUND " + target + " op=" + assign.operator());
        }
        Operators.Rep left = readValue(state, target);
        Operators.Rep right = emitExpression(state, assign.value());
        String operator = assign.operator().substring(0, assign.operator().length() - 1);
        Operators.binary(state.code, operator, left, right);
        Operators.Rep result = Operators.repOf(
                Operators.resultType(operator, left.astType(), right.astType(), false));
        if (System.getenv("FASTSCRIPT_TRACE") != null) {
            System.err.println("  COMPOUND " + target + " op=" + assign.operator()
                    + " leftRep=" + left.jvmType() + " rightRep=" + right.jvmType()
                    + " resultRep=" + result.jvmType());
        }
        writeValue(state, target, result);
    }

    /**
     * {@code loop N times:} — iterates from zero to {@code N-1}, exposing the iteration number
     * as the {@code loop-index} variable.
     *
     * <p>Two separate JVM locals are reserved for the count and the running index. Reusing one
     * slot for both would overwrite the requested count before it is ever compared, which is
     * exactly why the bound is read from a distinct slot here.</p>
     */
    private void compileLoopTimes(MethodState state, LoopTimes loop, Body body) {
        int limit = state.reserveDouble();
        int index = state.reserveDouble();

        pushExpression(state, loop.count(), Operators.Rep.NUMBER);
        state.code.storeDouble(limit);
        state.code.dconst(0);
        state.code.storeDouble(index);

        Label test = new Label();
        Label bodyLabel = new Label();
        Label advance = new Label();
        Label done = new Label();

        state.code.label(test);
        state.code.loadDouble(index);
        state.code.loadDouble(limit);
        state.code.dcmpg();
        state.code.jump(Opcodes.IFLT, bodyLabel);
        state.code.jump(Opcodes.GOTO, done);
        state.code.label(bodyLabel);

        // loop-index is a real local holding a double, so the body compiles to primitive maths.
        int bodyLocals = state.nextLocal;
        state.scopes.push(new LinkedHashMap<>());
        state.scopes.peek().put("loop-index", new Storage.DoubleLocal(index));
        state.loops.push(new LoopLabels(advance, done));
        for (Stmt statement : loop.body()) {
            compileStatement(state, statement, body);
        }
        state.loops.pop();
        state.scopes.pop();
        // The bound and index stay reserved across the whole loop; body locals are released.
        state.nextLocal = Math.max(bodyLocals, index + 2);

        state.code.label(advance);
        state.code.loadDouble(index);
        state.code.dconst(1);
        state.code.dadd();
        state.code.storeDouble(index);
        state.code.jump(Opcodes.GOTO, test);
        state.code.label(done);
        
    }

    /** Iterates a list value using two JVM locals for the iterator and the current element. */
    private void compileForEach(MethodState state, ForEach loop, Body body) {
        int iterator = state.reserveReference();
        int element = state.reserveReference();

        pushReference(state, loop.source());
        state.code.checkCast("java/util/List");
        state.code.invokeInterface("java/util/List", "iterator", "()Ljava/util/Iterator;");
        state.code.storeRef(iterator);

        Label test = new Label();
        Label bodyLabel = new Label();
        Label advance = new Label();
        Label done = new Label();
        state.code.label(test);
        state.code.loadRef(iterator);
        state.code.invokeInterface("java/util/Iterator", "hasNext", "()Z");
        state.code.jump(Opcodes.IFNE, bodyLabel);
        state.code.jump(Opcodes.GOTO, done);
        state.code.label(bodyLabel);
        state.code.loadRef(iterator);
        state.code.invokeInterface("java/util/Iterator", "next", "()" + OBJECT_DESC);
        state.code.storeRef(element);

        int bodyLocals = state.nextLocal;
        state.scopes.push(new LinkedHashMap<>());
        state.scopes.peek().put(loop.variable(), new Storage.ObjectLocal(element));
        state.loops.push(new LoopLabels(advance, done));
        for (Stmt statement : loop.body()) {
            compileStatement(state, statement, body);
        }
        state.loops.pop();
        state.scopes.pop();
        state.nextLocal = bodyLocals;
        state.code.label(advance);
        state.code.jump(Opcodes.GOTO, test);
        state.code.label(done);
        
    }

    private void compileReturn(MethodState state, Return statement) {
        Ast.Type declared = state.returnType;
        if (declared == Ast.Type.VOID) {
            if (statement.value() != null) {
                throw error(statement.pos(), "this handler does not return a value");
            }
            state.code.returnValue("V");
            return;
        }
        Operators.Rep wanted = Operators.repOf(declared);
        if (statement.value() == null) {
            switch (declared) {
                case NUMBER -> state.code.dconst(0);
                case BOOL -> state.code.iconst(0);
                default -> state.code.aconst(null);
            }
        } else {
            pushExpression(state, statement.value(), wanted);
        }
        state.code.returnValue(wanted.jvmType());
    }

    private void compileEffect(MethodState state, String name, List<Expr> arguments, Ast.Pos pos) {
        Builtins.BuiltinSpec spec = Builtins.lookup(name, arguments.size());
        if (spec == null) {
            throw error(pos, "unknown statement or function '" + name + "'");
        }
        Operators.Rep produced = emitCall(state, name, arguments, spec, pos);
        if (!produced.isVoid()) {
            discard(state, produced);
        }
    }

    // ------------------------------------------------------------------ expressions

    /** Emits an expression as a reference value. */
    private Operators.Rep pushExpression(MethodState state, Expr expression) {
        return pushExpression(state, expression, Operators.Rep.OBJECT);
    }

    /**
     * Emits an expression and converts the result to {@code wanted}.
     *
     * <p>Requesting {@link Operators.Rep#OBJECT} is a real guarantee: the value on top of the
     * stack is a reference, boxed here if the expression produced a primitive.</p>
     */
    private Operators.Rep pushExpression(MethodState state, Expr expression, Operators.Rep wanted) {
        Operators.Rep produced = emitExpression(state, expression);
        if (produced.jvmType().equals(wanted.jvmType())) {
            return produced;
        }
        if (wanted.isObject()) {
            boxForReference(state, expression, produced);
            return Operators.Rep.OBJECT;
        }
        Operators.convert(state.code, produced, wanted);
        return wanted;
    }

    /**
     * Boxes a value that must end up as a reference.
     *
     * <p>The decision is driven by the expression's <em>kind</em> rather than by the emitted
     * representation: an expression can be statically numeric and still emit a helper call that
     * already returns an object, and boxing that again would corrupt the stack.</p>
     */
    private Operators.Rep boxForReference(MethodState state, Expr expression, Operators.Rep produced) {
        Ast.Type kind = produced.kind();
        if (kind == Ast.Type.ANY) {
            kind = kindOf(state, expression);
        }
        // Trust what was actually emitted when it is already a reference: a helper call can
        // return an object even though the expression is statically numeric.
        if (produced.isObject()) {
            return Operators.Rep.OBJECT;
        }
        if (kind == Ast.Type.NUMBER || kind == Ast.Type.BOOL) {
            state.code.box(produced.jvmType());
            return Operators.Rep.OBJECT;
        }
        return produced;
    }

    private void pushBoolean(MethodState state, Expr expression) {
        pushExpression(state, expression, Operators.Rep.BOOL);
    }

    /** Emits an expression and guarantees a reference on the stack. */
    private Operators.Rep pushReference(MethodState state, Expr expression) {
        return pushExpression(state, expression, Operators.Rep.OBJECT);
    }

    private Operators.Rep emitExpression(MethodState state, Expr expression) {
        return switch (expression) {
            case Literal literal -> pushLiteral(state, literal);
            case Local local -> readLocal(state, local);
            case Global global -> {
                requireContext(state, global.pos(), "global variables ($x)");
                yield readGlobal(state, global.name());
            }
            case PlayerVar variable -> {
                requireContext(state, variable.pos(), "player variables (#x)");
                yield readPlayerVariable(state, variable.name());
            }
            case Binary binary -> pushBinary(state, binary);
            case Unary unary -> pushUnary(state, unary);
            case Call call -> pushCall(state, call);
            case Property property -> {
                requireContext(state, property.pos(), "property reads");
                yield pushLikeGet(state, property.receiver(), property.name());
            }
            case Index index -> {
                requireContext(state, index.pos(), "index reads");
                yield pushLikeGet(state, index.receiver(), index.key());
            }
            case Ternary ternary -> pushTernary(state, ternary);
        };
    }

    private Operators.Rep pushLiteral(MethodState state, Literal literal) {
        return switch (literal.type()) {
            case NUMBER -> {
                state.code.dconst(((Number) literal.value()).doubleValue());
                yield Operators.Rep.NUMBER;
            }
            case TEXT -> {
                state.code.aconst((String) literal.value());
                yield Operators.Rep.TEXT;
            }
            case BOOL -> {
                state.code.iconst(((Boolean) literal.value()) ? 1 : 0);
                yield Operators.Rep.BOOL;
            }
            default -> {
                state.code.aconst(null);
                yield Operators.Rep.OBJECT;
            }
        };
    }

    private Operators.Rep pushBinary(MethodState state, Binary binary) {
        String operator = binary.operator();
        if (operator.equals("and") || operator.equals("or")) {
            boolean isAnd = operator.equals("and");
            Label shortCircuit = new Label();
            Label done = new Label();
            pushBoolean(state, binary.left());
            state.code.jump(isAnd ? Opcodes.IFEQ : Opcodes.IFNE, shortCircuit);
            pushBoolean(state, binary.right());
            state.code.jump(Opcodes.GOTO, done);
            state.code.label(shortCircuit);
            state.code.iconst(isAnd ? 0 : 1);
            state.code.label(done);
            return Operators.Rep.BOOL;
        }

        Ast.Type leftKind = kindOf(state, binary.left());
        Ast.Type rightKind = kindOf(state, binary.right());
        boolean numeric = leftKind == Ast.Type.NUMBER && rightKind == Ast.Type.NUMBER;
        boolean concatenation = operator.equals("+")
                && !numeric
                && (leftKind == Ast.Type.TEXT || rightKind == Ast.Type.TEXT);

        if (numeric) {
            // Both operands are provably numbers, so this compiles to primitive instructions.
            pushExpression(state, binary.left(), Operators.Rep.NUMBER);
            pushExpression(state, binary.right(), Operators.Rep.NUMBER);
            Operators.binary(state.code, operator, Operators.Rep.NUMBER, Operators.Rep.NUMBER);
            return Operators.repOf(Operators.resultType(operator, Ast.Type.NUMBER, Ast.Type.NUMBER, false));
        }

        // Mixed and dynamic operands are boxed one at a time, immediately after being pushed:
        // boxing later would apply the conversion to whatever value is on top by then.
        // Both sides share the same representation at the end, which is what the comparison,
        // equality and arithmetic helpers all expect.
        boolean bothText = leftKind == Ast.Type.TEXT && rightKind == Ast.Type.TEXT;
        boolean comparison = switch (operator) {
            case "==", "!=", "<", ">", "<=", ">=", "contains", "is" -> true;
            default -> false;
        };
        if (bothText && comparison) {
            // Text comparison stays reference-typed, so no boxing is needed.
            pushExpression(state, binary.left(), Operators.Rep.TEXT);
            pushExpression(state, binary.right(), Operators.Rep.TEXT);
            Operators.binary(state.code, operator, Operators.Rep.TEXT, Operators.Rep.TEXT);
            return Operators.repOf(
                    Operators.resultType(operator, Ast.Type.TEXT, Ast.Type.TEXT, false));
        }

        Operators.Rep left = boxedPush(state, binary.left(), leftKind);
        Operators.Rep right = boxedPush(state, binary.right(), rightKind);
        if (concatenation || operator.equals("<<<") || operator.equals(">>>")) {
            // Concatenation always goes through the helper, so numbers, booleans and nulls all
            // render the way scripts expect. The result is a reference, and reporting it as
            // OBJECT keeps later boxing decisions honest.
            state.code.invokeStatic(Operators.VALUES_OWNER, "add",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            return Operators.Rep.OBJECT;
        }
        // Any operator that reaches the helper returns an object, whatever its static kind was.
        boolean helperResult = switch (operator) {
            case "-", "*", "/", "%" -> !(left.isNumber() && right.isNumber());
            case "+", "contains" -> true;
            default -> false;
        };
        if (helperResult) {
            Operators.binary(state.code, operator, left, right);
            return Operators.Rep.OBJECT;
        }
        Operators.Rep result = switch (operator) {
            case "==" -> {
                Operators.equality(state.code, left, right);
                yield Operators.Rep.BOOL;
            }
            case "!=" -> {
                Operators.inequality(state.code, left, right);
                yield Operators.Rep.BOOL;
            }
            case "<" -> {
                Operators.ordered(state.code, "<", left, right);
                yield Operators.Rep.BOOL;
            }
            case ">" -> {
                Operators.ordered(state.code, ">", left, right);
                yield Operators.Rep.BOOL;
            }
            case "<=" -> {
                Operators.ordered(state.code, "<=", left, right);
                yield Operators.Rep.BOOL;
            }
            case ">=" -> {
                Operators.ordered(state.code, ">=", left, right);
                yield Operators.Rep.BOOL;
            }
            default -> {
                Operators.binary(state.code, operator, left, right);
                yield Operators.repOf(
                        Operators.resultType(operator, left.astType(), right.astType(), concatenation));
            }
        };
        return result;
    }

    /**
     * Pushes an operand as a reference and reports the matching representation.
     *
     * <p>A statically numeric or boolean operand becomes a primitive on the stack, so it is
     * boxed; an operand that already produced a reference is used as-is. The reported
     * representation always describes what is actually on the stack, which is what keeps the
     * helper call shapes valid.</p>
     */
    private Operators.Rep boxedPush(MethodState state, Expr expression, Ast.Type kind) {
        Operators.Rep produced = emitExpression(state, expression);
        if (produced.isObject()) {
            return produced;
        }
        if (kind == Ast.Type.NUMBER || kind == Ast.Type.BOOL) {
            state.code.box(produced.jvmType());
            return Operators.Rep.OBJECT;
        }
        // A text operand is already a reference.
        return produced;
    }

    /**
     * Best-effort static kind of an expression.
     *
     * <p>Only leaves with a reliable kind are reported: literals, locals whose declared kind is
     * still consistent, and untyped storage (globals and player variables) which stays dynamic.
     * Returning a precise kind here is what lets the compiler emit primitive arithmetic.</p>
     */
    private Ast.Type kindOf(MethodState state, Expr expression) {
        return switch (expression) {
            case Literal literal -> literal.type();
            case Local local -> {
                Storage storage = state.resolve(local.name());
                yield storage == null ? Ast.Type.ANY : storage.kind();
            }
            case Unary unary -> unary.operator().equals("!")
                    ? Ast.Type.BOOL
                    : kindOf(state, unary.operand());
            case Binary binary -> {
                Ast.Type left = kindOf(state, binary.left());
                Ast.Type right = kindOf(state, binary.right());
                yield Operators.resultType(binary.operator(), left, right, false);
            }
            case Call call -> {
                Builtins.BuiltinSpec spec = Builtins.lookup(call.name(), call.arguments().size());
                yield spec == null ? Ast.Type.ANY : spec.returnType();
            }
            default -> Ast.Type.ANY;
        };
    }

    private Operators.Rep pushUnary(MethodState state, Unary unary) {
        String operator = unary.operator();
        switch (operator) {
            case "!" -> {
                pushBoolean(state, unary.operand());
                state.code.iconst(1);
                state.code.ixor();
                return Operators.Rep.BOOL;
            }
            case "-" -> {
                pushExpression(state, unary.operand(), Operators.Rep.NUMBER);
                state.code.dneg();
                return Operators.Rep.NUMBER;
            }
            case "++", "--", "post++", "post--" -> {
                return pushIncrement(state, unary, operator);
            }
            default -> throw error(unary.pos(), "unsupported unary operator '" + operator + "'");
        }
    }

    private Operators.Rep pushIncrement(MethodState state, Unary unary, String operator) {
        boolean postfix = operator.startsWith("post");
        double delta = operator.endsWith("++") ? 1.0 : -1.0;
        Target target = asTarget(unary.operand(), unary.pos());

        // Read, keep the old value when needed, then apply and write back the new value.
        int previous = -1;
        Operators.Rep current = readValue(state, target);
        Operators.convert(state.code, current, Operators.Rep.NUMBER);
        if (postfix) {
            previous = state.scratchDouble();
            state.code.storeDouble(previous);
        }
        state.code.dconst(delta);
        state.code.dadd();
        writeNumber(state, target);
        if (postfix) {
            state.code.loadDouble(previous);
        }
        return Operators.Rep.NUMBER;
    }

    /** Writes a double currently on the stack to a target, consuming it. */
    private void writeNumber(MethodState state, Target target) {
        state.code.box(Code.DOUBLE);
        writeValue(state, target, Operators.Rep.OBJECT);
    }

    private Operators.Rep pushCall(MethodState state, Call call) {
        Builtins.BuiltinSpec spec = Builtins.lookup(call.name(), call.arguments().size());
        if (spec != null) {
            return emitCall(state, call.name(), call.arguments(), spec, call.pos());
        }
        FunctionSignature signature = functions.get(call.name());
        if (signature == null) {
            throw error(call.pos(), "unknown function '" + call.name() + "'");
        }
        if (signature.parameters().size() != call.arguments().size()) {
            throw error(call.pos(), "function '" + call.name() + "' expects "
                    + signature.parameters().size() + " argument(s), got " + call.arguments().size());
        }
        for (Expr argument : call.arguments()) {
            pushReference(state, argument);
        }
        state.code.invokeStatic(className, signature.methodName(), signature.descriptor());
        return Operators.repOf(signature.returnType());
    }

    private Operators.Rep emitCall(MethodState state, String name, List<Expr> arguments,
            Builtins.BuiltinSpec spec, Ast.Pos pos) {
        if (spec.variadic() || spec.arity() != arguments.size()) {
            requireContext(state, pos, "'" + name + "'");
            return emitDispatch(state, name, arguments);
        }
        // Context builtins are instance methods on the ExecContext in slot 1: the receiver
        // goes first so the arguments land on top of it in order.
        if (!spec.staticCall()) {
            requireContext(state, pos, "'" + name + "'");
            state.code.loadRef(1);
        }
        for (int index = 0; index < arguments.size(); index++) {
            Operators.Rep wanted = Operators.repOf(spec.parameterTypes()[index]);
            Operators.Rep actual = emitExpression(state, arguments.get(index));
            if (actual.jvmType().equals(wanted.jvmType())) {
                continue;
            }
            if (wanted.isObject() || actual.isObject() || isLooselyCompatible(actual, wanted)) {
                Operators.convert(state.code, actual, wanted);
                continue;
            }
            throw error(arguments.get(index).pos(), "argument " + (index + 1) + " of '" + name
                    + "' must be " + describe(wanted) + ", got " + describe(actual));
        }
        String methodName = methodNameOf(name);
        if (spec.staticCall()) {
            state.code.invokeStatic(Builtins.FUNCTIONS_OWNER, methodName, spec.methodDescriptor());
        } else {
            state.code.invokeVirtual(CONTEXT_OWNER, methodName, spec.methodDescriptor());
        }
        return Operators.repOf(spec.returnType());
    }

    private boolean isLooselyCompatible(Operators.Rep actual, Operators.Rep wanted) {
        return (actual.isText() && (wanted.isNumber() || wanted.isBool()))
                || (actual.isNumber() && (wanted.isText() || wanted.isBool()))
                || (actual.isBool() && (wanted.isNumber() || wanted.isText()));
    }

    private String describe(Operators.Rep rep) {
        return switch (rep.jvmType()) {
            case "D" -> "a number";
            case "Z" -> "a boolean";
            case STRING_DESC -> "text";
            default -> "a value";
        };
    }

    /** Script name to Java method name: kebab-case becomes camelCase. */
    private static String methodNameOf(String builtin) {
        StringBuilder builder = new StringBuilder(builtin.length());
        boolean upper = false;
        for (int i = 0; i < builtin.length(); i++) {
            char character = builtin.charAt(i);
            if (character == '-') {
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return builder.toString();
    }

    private Operators.Rep emitDispatch(MethodState state, String name, List<Expr> arguments) {
        state.code.loadRef(1);
        state.code.newObjectArray(arguments.size() + 1);
        // Each store consumes its array reference, so duplicate it first to keep the array
        // available for the next element and for the final dispatch call.
        state.code.dup();
        state.code.iconst(0);
        state.code.aconst(name);
        state.code.aastore();
        for (int index = 0; index < arguments.size(); index++) {
            state.code.dup();
            state.code.iconst(index + 1);
            pushReference(state, arguments.get(index));
            state.code.aastore();
        }
        state.code.invokeStatic(Builtins.FUNCTIONS_OWNER, "dispatch", Builtins.DISPATCH_DESCRIPTOR);
        return Operators.Rep.OBJECT;
    }

    /** Emits {@code Functions.dispatch(ctx, ["get", receiver, key])} for property and index reads. */
    private Operators.Rep pushLikeGet(MethodState state, Expr receiver, Object key) {
        state.code.loadRef(1);
        state.code.newObjectArray(3);
        state.code.dup();
        state.code.iconst(0);
        state.code.aconst("get");
        state.code.aastore();
        state.code.dup();
        state.code.iconst(1);
        pushReference(state, receiver);
        state.code.aastore();
        state.code.dup();
        state.code.iconst(2);
        pushKey(state, key);
        state.code.aastore();
        state.code.invokeStatic(Builtins.FUNCTIONS_OWNER, "dispatch", Builtins.DISPATCH_DESCRIPTOR);
        return Operators.Rep.OBJECT;
    }

    private void pushKey(MethodState state, Object key) {
        if (key instanceof Expr keyExpression) {
            pushReference(state, keyExpression);
        } else {
            state.code.aconst((String) key);
        }
    }

    private Operators.Rep pushTernary(MethodState state, Ternary ternary) {
        Label whenFalse = new Label();
        Label done = new Label();
        pushBoolean(state, ternary.test());
        state.code.jump(Opcodes.IFEQ, whenFalse);
        pushReference(state, ternary.whenTrue());
        state.code.jump(Opcodes.GOTO, done);
        state.code.label(whenFalse);
        pushReference(state, ternary.whenFalse());
        state.code.label(done);
        return Operators.Rep.OBJECT;
    }

    // ------------------------------------------------------------------ reads and writes

    private Operators.Rep readLocal(MethodState state, Local local) {
        Storage storage = state.resolve(local.name());
        if (storage == null) {
            throw error(local.pos(), "unknown variable '" + local.name() + "'");
        }
        return loadStorage(state, storage, Operators.repOf(storage.kind()));
    }

    private Operators.Rep readGlobal(MethodState state, String name) {
        state.code.loadRef(1);
        state.code.aconst(name);
        state.code.invokeVirtual(CONTEXT_OWNER, "variable", "(Ljava/lang/String;)" + OBJECT_DESC);
        return Operators.Rep.OBJECT;
    }

    private Operators.Rep readPlayerVariable(MethodState state, String name) {
        state.code.loadRef(1);
        state.code.aconst(name);
        state.code.invokeVirtual(CONTEXT_OWNER, "playerVariable", "(Ljava/lang/String;)" + OBJECT_DESC);
        return Operators.Rep.OBJECT;
    }

    private Operators.Rep readValue(MethodState state, Target target) {
        if (target instanceof GlobalTarget || target instanceof PlayerTarget
                || target instanceof IndexTarget) {
            requireContext(state, target.pos(), "this assignment target");
        }
        return switch (target) {
            case LocalTarget local -> readLocal(state, new Local(local.name(), local.pos()));
            case GlobalTarget global -> readGlobal(state, global.name());
            case PlayerTarget player -> readPlayerVariable(state, player.name());
            case IndexTarget index -> pushLikeGet(state, index.receiver(), index.key());
        };
    }

    /** Stores the value currently on the stack, which is consumed. */
    private void writeValue(MethodState state, Target target, Operators.Rep value) {
        writeValue(state, target, value, value.kind());
    }

    /**
     * Stores the value currently on the stack, which is consumed.
     *
     * @param observed kind the stored expression is known to produce, used to refine local
     *                 variable types for later primitive arithmetic
     */
    private void writeValue(MethodState state, Target target, Operators.Rep value, Ast.Type observed) {
        if (target instanceof GlobalTarget || target instanceof PlayerTarget
                || target instanceof IndexTarget) {
            requireContext(state, target.pos(), "this assignment target");
        }
        switch (target) {
            case LocalTarget local -> {
                Storage storage = state.resolve(local.name());
                if (storage == null) {
                    storage = state.declareLocal(local.name(), Ast.Type.ANY);
                }
                storage.refine(observed);
                storeStorage(state, storage, value);
            }
            case GlobalTarget global -> {
                int scratch = stash(state, value);
                state.code.loadRef(1);
                state.code.aconst(global.name());
                state.code.loadRef(scratch);
                state.code.invokeVirtual(CONTEXT_OWNER, "setGlobal",
                        "(Ljava/lang/String;" + OBJECT_DESC + ")V");
            }
            case PlayerTarget player -> {
                int scratch = stash(state, value);
                state.code.loadRef(1);
                state.code.aconst(player.name());
                state.code.loadRef(scratch);
                state.code.invokeVirtual(CONTEXT_OWNER, "setPlayerVariable",
                        "(Ljava/lang/String;" + OBJECT_DESC + ")V");
            }
            case IndexTarget index -> {
                int scratch = stash(state, value);
                state.code.loadRef(1);
                state.code.newObjectArray(4);
                state.code.dup();
                state.code.iconst(0);
                state.code.aconst("assignIndex");
                state.code.aastore();
                state.code.dup();
                state.code.iconst(1);
                pushReference(state, index.receiver());
                state.code.aastore();
                state.code.dup();
                state.code.iconst(2);
                pushReference(state, index.key());
                state.code.aastore();
                state.code.dup();
                state.code.iconst(3);
                state.code.loadRef(scratch);
                state.code.aastore();
                state.code.invokeStatic(Builtins.FUNCTIONS_OWNER, "dispatch", Builtins.DISPATCH_DESCRIPTOR);
                state.code.pop();
            }
        }
    }

    /** Moves the value on the stack into a JVM local as a reference, returning its index. */
    private int stash(MethodState state, Operators.Rep value) {
        Operators.boxToObject(state.code, value);
        int slot = state.scratchLocal();
        state.code.storeRef(slot);
        return slot;
    }

    private Target asTarget(Expr expression, Ast.Pos pos) {
        if (expression instanceof Local local) {
            return new LocalTarget(local.name(), local.pos());
        }
        if (expression instanceof Global global) {
            return new GlobalTarget(global.name(), global.pos());
        }
        if (expression instanceof PlayerVar variable) {
            return new PlayerTarget(variable.name(), variable.pos());
        }
        if (expression instanceof Index index) {
            return new IndexTarget(index.receiver(), index.key(), index.pos());
        }
        throw error(pos, "expected an assignable target");
    }

    private void discard(MethodState state, Operators.Rep rep) {
        switch (rep.jvmType()) {
            case "D", "J" -> state.code.pop2();
            case "V" -> {
                // Nothing on the stack.
            }
            default -> state.code.pop();
        }
    }

    // ------------------------------------------------------------------ storage model

    /**
     * Where a script variable lives.
     *
     * <p>{@link JvmLocal} uses real JVM slots (fastest), {@link FrameSlot} uses the execution
     * context array (needed for loop state that outlives nested statements), and
     * {@link Argument} reads a command argument straight from the invocation array.</p>
     *
     * <p>{@link #kind()} additionally tracks the value kind a variable is known to hold. It is a
     * best-effort promise used for primitive arithmetic: when it becomes {@code ANY} the
     * compiler falls back to dynamic helpers, so a wrong guess can only cost speed, not
     * correctness.</p>
     */
    private sealed interface Storage permits Storage.JvmLocal, Storage.DoubleLocal,
            Storage.ObjectLocal, Storage.Argument {

        Ast.Type type();

        /** Known value kind, or {@link Ast.Type#ANY} when it cannot be proven. */
        Ast.Type kind();

        /** Narrows the known kind; a conflict degrades to {@code ANY}. */
        void refine(Ast.Type observed);

        final class JvmLocal implements Storage {

            private final int index;
            /** Fixed kind for values that arrive from outside the script, such as parameters. */
            private final Ast.Type fixedKind;
            private Ast.Type kind;
            private boolean kindKnown;

            JvmLocal(int index) {
                this(index, null);
            }

            JvmLocal(int index, Ast.Type fixedKind) {
                this.index = index;
                this.fixedKind = fixedKind;
                this.kind = fixedKind == null ? Ast.Type.ANY : fixedKind;
                this.kindKnown = fixedKind != null;
            }

            int index() {
                return index;
            }

            @Override
            public Ast.Type type() {
                return Ast.Type.ANY;
            }

            @Override
            public Ast.Type kind() {
                return kind;
            }

            /**
             * Records the kind of the value just stored. The first store establishes the kind;
             * later stores must agree, otherwise the variable degrades to dynamic. Parameters
             * are fixed to dynamic because callers can pass any value kind.
             */
            @Override
            public void refine(Ast.Type observed) {
                if (fixedKind != null) {
                    return;
                }
                if (!kindKnown) {
                    if (observed == null || observed == Ast.Type.ANY || observed == Ast.Type.VOID) {
                        return;
                    }
                    kind = observed;
                    kindKnown = true;
                    return;
                }
                kind = mergeKinds(kind, observed);
            }

            @Override
            public String toString() {
                return "JvmLocal[index=" + index + ", kind=" + kind + "]";
            }
        }

        record DoubleLocal(int index) implements Storage {

            @Override
            public Ast.Type type() {
                return Ast.Type.NUMBER;
            }

            @Override
            public Ast.Type kind() {
                return Ast.Type.NUMBER;
            }

            @Override
            public void refine(Ast.Type observed) {
                // Internal slots keep their primitive type.
            }
        }

        record ObjectLocal(int index) implements Storage {

            @Override
            public Ast.Type type() {
                return Ast.Type.ANY;
            }

            @Override
            public Ast.Type kind() {
                return Ast.Type.ANY;
            }

            @Override
            public void refine(Ast.Type observed) {
                // Internal slots keep their reference type.
            }
        }

        record Argument(int position, Ast.Type type) implements Storage {

            @Override
            public Ast.Type kind() {
                return type;
            }

            @Override
            public void refine(Ast.Type observed) {
                // Command arguments are always text.
            }
        }
    }

    /** Merges two inferred kinds; differing kinds mean the value is dynamic. */
    private static Ast.Type mergeKinds(Ast.Type current, Ast.Type observed) {
        if (current == null) {
            return observed;
        }
        if (observed == null || observed == Ast.Type.ANY || observed == Ast.Type.VOID) {
            return Ast.Type.ANY;
        }
        if (current == Ast.Type.ANY) {
            return Ast.Type.ANY;
        }
        return current == observed ? current : Ast.Type.ANY;
    }

    /** Loads a storage location in a requested representation. */
    private Operators.Rep loadStorage(MethodState state, Storage storage, Operators.Rep wanted) {
        Operators.Rep natural = switch (storage) {
            case Storage.JvmLocal local -> {
                state.code.loadRef(local.index());
                yield Operators.Rep.OBJECT;
            }
            case Storage.DoubleLocal slot -> {
                state.code.loadDouble(slot.index());
                yield Operators.Rep.NUMBER;
            }
            case Storage.ObjectLocal slot -> {
                state.code.loadRef(slot.index());
                yield Operators.Rep.OBJECT;
            }
            case Storage.Argument argument -> {
                state.code.loadRef(1);
                state.code.iconst(argument.position());
                state.code.invokeVirtual(CONTEXT_OWNER, "arg", "(I)" + OBJECT_DESC);
                yield Operators.Rep.OBJECT;
            }
        };
        if (!natural.jvmType().equals(wanted.jvmType())) {
            Operators.convert(state.code, natural, wanted);
            return wanted;
        }
        return natural;
    }

    /**
     * Stores a value currently on the stack, consuming it.
     *
     * <p>Script variables hold references, so the value keeps its own kind: a double is boxed as
     * {@link Double}, text stays a {@link String}. Internal loop slots are typed and receive the
     * primitive directly.</p>
     */
    private void storeStorage(MethodState state, Storage storage, Operators.Rep value) {
        switch (storage) {
            case Storage.JvmLocal local -> {
                if (!value.isObject()) {
                    state.code.box(value.jvmType());
                }
                state.code.storeRef(local.index());
            }
            case Storage.DoubleLocal slot -> {
                Operators.convert(state.code, value, Operators.Rep.NUMBER);
                state.code.storeDouble(slot.index());
            }
            case Storage.ObjectLocal slot -> {
                if (!value.isObject()) {
                    state.code.box(value.jvmType());
                }
                state.code.storeRef(slot.index());
            }
            case Storage.Argument argument -> throw new IllegalStateException(
                    "command arguments are read-only (position " + argument.position() + ")");
        }
    }

    // ------------------------------------------------------------------ method state

    private record LoopLabels(Label continueTarget, Label breakTarget) {
    }

    /** Mutable compilation state for the method currently being emitted. */
    private final class MethodState {

        private final Code code;
        private final Deque<Map<String, Storage>> scopes = new ArrayDeque<>();
        private final Deque<LoopLabels> loops = new ArrayDeque<>();
        private int nextLocal;
        private int scratch = -1;
        private int scratchDouble = -1;
        private Ast.Type returnType = Ast.Type.VOID;
        /**
         * True inside trigger and command handlers, whose first slots hold the argument
         * array and the {@code ExecContext}. Script functions take only their parameters,
         * so anything reaching for the context (effects, {@code $}/{@code #}, dynamic
         * calls) is rejected with a readable error instead of corrupt bytecode.
         */
        private boolean hasContext;

        MethodState(Code code, int baseLocals) {
            this.code = code;
            this.nextLocal = baseLocals;
        }

        Storage resolve(String name) {
            for (Map<String, Storage> scope : scopes) {
                Storage storage = scope.get(name);
                if (storage != null) {
                    return storage;
                }
            }
            return null;
        }

        /**
         * Reusable reference local for moving stack values around.
         *
         * <p>Allocated lazily above every script local and never released, so it cannot collide
         * with a local declared later, nor with the context parameter in slot 1. Because it is
         * only used inside a single statement — never across an expression evaluation — reusing
         * one slot is safe.</p>
         */
        int scratchLocal() {
            if (scratch < 0) {
                scratch = nextLocal++;
            }
            return scratch;
        }

        /** Second scratch slot, used by postfix increment to keep the previous value. */
        int scratchDouble() {
            if (scratchDouble < 0) {
                scratchDouble = nextLocal;
                nextLocal += 2;
            }
            return scratchDouble;
        }

        /** Declares a script-visible local backed by a JVM slot; its kind is inferred later. */
        Storage declareLocal(String name, Ast.Type type) {
            Storage storage = new Storage.JvmLocal(nextLocal++);
            scopes.peek().put(name, storage);
            return storage;
        }

        /** Reserves one JVM local holding a primitive double for the duration of a loop. */
        int reserveDouble() {
            int index = nextLocal;
            nextLocal += 2;
            return index;
        }

        /** Reserves one reference-typed JVM local for the duration of a loop. */
        int reserveReference() {
            return nextLocal++;
        }
    }

    private MethodState begin(MethodVisitor plain, int baseLocals) {
        plain.visitCode();
        return new MethodState(new Code(plain), baseLocals);
    }

    private void finish(MethodState state) {
        state.code.raw().visitMaxs(0, 0);
        state.code.raw().visitEnd();
    }

    private void requireContext(MethodState state, Ast.Pos pos, String what) {
        if (!state.hasContext) {
            throw error(pos, what + " needs an event or command context"
                    + " and cannot be used in a function");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String sanitize(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            builder.append(Character.isLetterOrDigit(character) ? character : '_');
        }
        return builder.toString();
    }

    private ScriptException error(Ast.Pos pos, String message) {
        return new ScriptException(message, sourceName, pos.line(), pos.column());
    }
}
