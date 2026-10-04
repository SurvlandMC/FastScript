package io.github.dsh.fastscript.lang;

import io.github.dsh.fastscript.ast.Ast;
import io.github.dsh.fastscript.ast.Ast.BreakLoop;
import io.github.dsh.fastscript.ast.Ast.Call;
import io.github.dsh.fastscript.ast.Ast.CommandDecl;
import io.github.dsh.fastscript.ast.Ast.Condition;
import io.github.dsh.fastscript.ast.Ast.ContinueLoop;
import io.github.dsh.fastscript.ast.Ast.Decl;
import io.github.dsh.fastscript.ast.Ast.Effect;
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
import io.github.dsh.fastscript.ast.Ast.Pos;
import io.github.dsh.fastscript.ast.Ast.Property;
import io.github.dsh.fastscript.ast.Ast.PropertyTarget;
import io.github.dsh.fastscript.ast.Ast.Script;
import io.github.dsh.fastscript.ast.Ast.Stop;
import io.github.dsh.fastscript.ast.Ast.Stmt;
import io.github.dsh.fastscript.ast.Ast.Target;
import io.github.dsh.fastscript.ast.Ast.Ternary;
import io.github.dsh.fastscript.ast.Ast.Trigger;
import io.github.dsh.fastscript.ast.Ast.While;
import io.github.dsh.fastscript.core.ScriptException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for the FastScript language.
 *
 * <p>The grammar is line oriented with significant indentation, which keeps scripts as
 * readable as Skript's while handing the compiler explicit block structure instead of
 * forcing it to re-derive nesting from a flat statement list.</p>
 */
public final class Parser {

    private static final Set<String> ASSIGN_OPERATORS =
            Set.of("=", "+=", "-=", "*=", "/=", "%=");

    private static final Set<String> COMPARISON_OPERATORS =
            Set.of("==", "!=", "<", ">", "<=", ">=");

    private final List<Token> tokens;
    private final String fileName;
    private int position;

    public Parser(List<Token> tokens, String fileName) {
        this.tokens = collapseBlankLines(tokens);
        this.fileName = fileName;
    }

    /**
     * Removes duplicate consecutive {@code NEWLINE} tokens.
     *
     * <p>A blank line ends the previous statement and then produces a newline of its own, which
     * would otherwise stop a block body from being recognised. Blank lines are not significant
     * in this grammar, so they are folded into the statement separator.</p>
     */
    private static List<Token> collapseBlankLines(List<Token> source) {
        List<Token> result = new ArrayList<>(source.size());
        Token previous = null;
        for (Token token : source) {
            if (token.type() == TokenType.NEWLINE && previous != null
                    && previous.type() == TokenType.NEWLINE) {
                continue;
            }
            result.add(token);
            previous = token;
        }
        return result;
    }

    public static Ast.Script parse(String source, String fileName) {
        return new Parser(new Lexer(source, fileName).tokenize(), fileName).parseScript();
    }

    /**
     * Parses a standalone expression such as a trigger filter, for example
     * {@code player.health < 20 and player.level > 5}.
     *
     * @param expression source text of the expression
     * @param fileName   file name used in diagnostics
     * @throws ScriptException when the text is not a single complete expression
     */
    public static Ast.Expr parseExpressionOnly(String expression, String fileName) {
        Parser parser = new Parser(new Lexer(expression, fileName).tokenize(), fileName);
        Ast.Expr parsed = parser.parseExpression();
        // A standalone snippet lexes with a trailing NEWLINE; it is not content.
        while (parser.check(TokenType.NEWLINE)) {
            parser.advance();
        }
        if (!parser.check(TokenType.EOF)) {
            throw parser.error(parser.peek(),
                    "unexpected " + parser.peek().describe() + " after the filter expression");
        }
        return parsed;
    }

    // ------------------------------------------------------------------ script

    public Ast.Script parseScript() {
        List<Decl> declarations = new ArrayList<>();
        skipNewlines();
        while (!check(TokenType.EOF)) {
            if (checkStructural("INDENT") || checkStructural("DEDENT")) {
                // Defensive: a stray structural token can only appear after a malformed block,
                // and skipping it keeps the remaining declarations parseable.
                advance();
                continue;
            }
            declarations.add(parseDeclaration());
            skipNewlines();
        }
        return new Script(fileName, List.copyOf(declarations));
    }

    private Decl parseDeclaration() {
        Token token = peek();
        if (token.isKeyword("on")) {
            return parseTrigger();
        }
        if (token.isKeyword("command")) {
            return parseCommand();
        }
        if (token.isKeyword("function")) {
            return parseFunction();
        }
        if (token.is(TokenType.GLOBAL_VAR) || token.is(TokenType.PLAYER_VAR)) {
            return parseGlobalDeclaration();
        }
        throw error(token, "expected 'on', 'command', 'function' or a variable declaration, found "
                + token.describe());
    }

    /** {@code on <event words> [where <condition>]:} — the event name is free-form text. */
    private Decl parseTrigger() {
        Token start = expectKeyword("on");
        StringBuilder eventName = new StringBuilder();
        while (!check(TokenType.NEWLINE) && !check(TokenType.EOF) && !checkPunct(":")) {
            if (check(TokenType.KEYWORD) && peek().isKeyword("where")) {
                break;
            }
            Token word = advance();
            if (eventName.length() > 0) {
                eventName.append(' ');
            }
            eventName.append(word.text());
        }
        String filter = null;
        if (check(TokenType.KEYWORD) && peek().isKeyword("where")) {
            advance();
            filter = captureExpressionText();
        }
        expectPunct(":");
        return new Trigger(eventName.toString().trim(), filter, parseBlock(), pos(start));
    }

    /**
     * {@code command name(arg1, arg2, rest: text) [permission x] [description y]:}
     *
     * <p>The trailing {@code : text} annotation marks the final argument as greedy, capturing
     * the remainder of the line.</p>
     */
    private Decl parseCommand() {
        Token start = expectKeyword("command");
        Token name = expect(TokenType.IDENT, "command name");
        List<String> arguments = new ArrayList<>();
        boolean greedy = false;
        if (checkPunct("(")) {
            advance();
            while (!checkPunct(")")) {
                Token argument = expect(TokenType.IDENT, "argument name");
                String argumentName = argument.text();
                if (checkPunct(":")) {
                    advance();
                    Token annotation = expect(TokenType.IDENT, "argument type");
                    greedy = annotation.text().equals("text");
                }
                arguments.add(argumentName);
                if (checkPunct(",")) {
                    advance();
                } else {
                    break;
                }
            }
            expectPunct(")");
        }
        String permission = null;
        String description = null;
        while (check(TokenType.IDENT)) {
            Token attribute = advance();
            switch (attribute.text()) {
                case "permission" -> permission = expectAttributeValue("permission value");
                case "description" -> description = expectAttributeValue("description value");
                default -> throw error(attribute, "unknown command attribute '" + attribute.text() + "'");
            }
        }
        expectPunct(":");
        return new CommandDecl(name.text(), List.copyOf(arguments), greedy, permission, description,
                parseBlock(), pos(start));
    }

    /** {@code function name(a, b) : number:} — the return type annotation is optional. */
    private Decl parseFunction() {
        Token start = expectKeyword("function");
        Token name = expect(TokenType.IDENT, "function name");
        List<String> parameters = new ArrayList<>();
        if (checkPunct("(")) {
            advance();
            while (!checkPunct(")")) {
                parameters.add(expect(TokenType.IDENT, "parameter name").text());
                if (checkPunct(",")) {
                    advance();
                } else {
                    break;
                }
            }
            expectPunct(")");
        }
        Ast.Type returnType = Ast.Type.VOID;
        if (checkPunct(":")) {
            // A colon at the end of the line is the block colon: a void function.
            // Otherwise it introduces the return type annotation.
            Token after = peekNext();
            if (after.type() != TokenType.NEWLINE && after.type() != TokenType.EOF) {
                advance();
                returnType = parseTypeName();
            }
        }
        expectPunct(":");
        return new FunctionDecl(name.text(), List.copyOf(parameters), returnType, parseBlock(), pos(start));
    }

    private Ast.Type parseTypeName() {
        Token type = expect(TokenType.IDENT, "type name");
        return switch (type.text()) {
            case "number" -> Ast.Type.NUMBER;
            case "text", "string" -> Ast.Type.TEXT;
            case "boolean", "bool" -> Ast.Type.BOOL;
            case "list" -> Ast.Type.LIST;
            case "map" -> Ast.Type.MAP;
            case "player" -> Ast.Type.PLAYER;
            default -> throw error(type, "unknown type '" + type.text() + "'");
        };
    }

    private Decl parseGlobalDeclaration() {
        boolean playerScoped = check(TokenType.PLAYER_VAR);
        Token start = advance();
        Ast.Expr initial = null;
        if (checkOperator("=")) {
            advance();
            initial = parseExpression();
        }
        return new GlobalVarDecl(start.text(), initial, playerScoped, pos(start));
    }

    // ------------------------------------------------------------------ statements

    private List<Stmt> parseBlock() {
        if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            throw error(peek(), "expected the end of the header line, found " + peek().describe());
        }
        advance();
        expectStructural("INDENT", "an indented block");
        List<Stmt> body = new ArrayList<>();
        skipNewlines();
        while (!checkStructural("DEDENT") && !checkStructural("INDENT") && !check(TokenType.EOF)) {
            body.add(parseStatement());
            skipNewlines();
        }
        if (checkStructural("DEDENT")) {
            advance();
        }
        if (body.isEmpty()) {
            throw error(peek(), "expected at least one statement in the block");
        }
        return List.copyOf(body);
    }

    private Stmt parseStatement() {
        Token token = peek();
        if (token.isKeyword("if")) {
            return parseIf();
        }
        if (token.isKeyword("while")) {
            return parseWhile();
        }
        if (token.isKeyword("loop")) {
            return parseLoopTimes();
        }
        if (token.isKeyword("for")) {
            return parseForEach();
        }
        if (token.isKeyword("break")) {
            advance();
            return new BreakLoop(pos(token));
        }
        if (token.isKeyword("continue")) {
            advance();
            return new ContinueLoop(pos(token));
        }
        if (token.isKeyword("stop")) {
            advance();
            return new Stop(pos(token));
        }
        if (token.isKeyword("return")) {
            advance();
            Ast.Expr value = null;
            if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                value = parseExpression();
            }
            return new Ast.Return(value, pos(token));
        }
        if (token.isKeyword("let")) {
            return parseLocalDeclaration();
        }
        if (isAssignmentAhead()) {
            return parseAssignment();
        }
        return parseEffectOrExpression();
    }

    /** {@code let name = value} — an explicit local declaration. */
    private Stmt parseLocalDeclaration() {
        Token start = expectKeyword("let");
        Token name = expect(TokenType.IDENT, "variable name");
        if (!checkOperator("=")) {
            throw error(peek(), "expected '=' after the declared variable name");
        }
        advance();
        Ast.Expr value = parseExpression();
        return new Ast.Assign(new LocalTarget(name.text(), pos(name)), "=", value, pos(start));
    }

    private Stmt parseIf() {
        Token start = expectKeyword("if");
        Ast.Expr test = parseExpression();
        expectPunct(":");
        List<Stmt> then = parseBlock();
        List<Stmt> otherwise = List.of();
        if (check(TokenType.NEWLINE)) {
            // A blank line between the branches is allowed and skipped.
            int mark = position;
            skipNewlines();
            if (!(check(TokenType.KEYWORD) && peek().isKeyword("else"))) {
                position = mark;
            }
        }
        if (check(TokenType.KEYWORD) && peek().isKeyword("else")) {
            advance();
            if (check(TokenType.KEYWORD) && peek().isKeyword("if")) {
                otherwise = List.of(parseIf());
            } else {
                expectPunct(":");
                otherwise = parseBlock();
            }
        }
        return new Condition(test, then, otherwise, pos(start));
    }

    private Stmt parseWhile() {
        Token start = expectKeyword("while");
        Ast.Expr test = parseExpression();
        expectPunct(":");
        return new While(test, parseBlock(), pos(start));
    }

    private Stmt parseLoopTimes() {
        Token start = expectKeyword("loop");
        Ast.Expr count = parseExpression();
        if (check(TokenType.KEYWORD) && peek().isKeyword("times")) {
            advance();
        }
        expectPunct(":");
        return new LoopTimes(count, parseBlock(), pos(start));
    }

    private Stmt parseForEach() {
        Token start = expectKeyword("for");
        Token variable = expect(TokenType.IDENT, "loop variable");
        if (!(check(TokenType.KEYWORD) && peek().isKeyword("in"))) {
            throw error(peek(), "expected 'in' after the loop variable");
        }
        advance();
        Ast.Expr source = parseExpression();
        expectPunct(":");
        return new ForEach(variable.text(), source, parseBlock(), pos(start));
    }

    private Stmt parseAssignment() {
        Token start = peek();
        Target target = parseAssignTarget();
        Token operator = expect(TokenType.OPERATOR, "assignment operator");
        if (!ASSIGN_OPERATORS.contains(operator.text())) {
            throw error(operator, "expected an assignment operator, found '" + operator.text() + "'");
        }
        Ast.Expr value = parseExpression();
        return new Ast.Assign(target, operator.text(), value, pos(start));
    }

    private Target parseAssignTarget() {
        Token token = peek();
        if (token.is(TokenType.GLOBAL_VAR)) {
            advance();
            return new GlobalTarget(token.text(), pos(token));
        }
        if (token.is(TokenType.PLAYER_VAR)) {
            advance();
            return new PlayerTarget(token.text(), pos(token));
        }
        if (token.is(TokenType.IDENT)) {
            advance();
            Ast.Expr receiver = new Local(token.text(), pos(token));
            return parsePostfixTarget(receiver, token);
        }
        throw error(token, "expected an assignment target, found " + token.describe());
    }

    private Target parsePostfixTarget(Ast.Expr receiver, Token start) {
        Ast.Expr current = receiver;
        while (true) {
            if (checkOperator(".")) {
                advance();
                Token property = expect(TokenType.IDENT, "property name");
                current = new Property(current, property.text(), pos(start));
                continue;
            }
            if (checkPunct("[")) {
                advance();
                Ast.Expr key = parseExpression();
                expectPunct("]");
                current = new Index(current, key, pos(start));
                continue;
            }
            break;
        }
        if (current instanceof Index index) {
            return new IndexTarget(index.receiver(), index.key(), pos(start));
        }
        if (current instanceof Property property) {
            return new PropertyTarget(property.receiver(), property.name(), pos(start));
        }
        if (current == receiver) {
            return new LocalTarget(((Local) receiver).name(), pos(start));
        }
        throw error(peek(), "cannot assign to this expression");
    }

    /** Distinguishes {@code x = 1} from an effect call such as {@code message "hi"}. */
    private boolean isAssignmentAhead() {
        if (check(TokenType.GLOBAL_VAR) || check(TokenType.PLAYER_VAR)) {
            return true;
        }
        if (!check(TokenType.IDENT)) {
            return false;
        }
        int depth = 0;
        for (int index = position; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.type() == TokenType.NEWLINE || token.type() == TokenType.EOF) {
                return false;
            }
            if (token.type() == TokenType.PUNCT) {
                switch (token.text()) {
                    case "(", "[", "{" -> depth++;
                    case ")", "]", "}" -> depth--;
                    case "INDENT", "DEDENT" -> {
                        return false;
                    }
                    default -> {
                    }
                }
                continue;
            }
            if (depth == 0 && token.type() == TokenType.OPERATOR
                    && ASSIGN_OPERATORS.contains(token.text())) {
                return true;
            }
        }
        return false;
    }

    private Stmt parseEffectOrExpression() {
        Token start = peek();
        // Bare effects take space-separated arguments without parentheses, for example
        // {@code message "hi"} or {@code give "DIAMOND" 3}. When a line starts with a name
        // followed by the start of another expression it can only be such a call: a binary
        // operator would continue the expression instead of starting a new one.
        if (start.type() == TokenType.IDENT && (followsBareArguments() || atEndOfLine())) {
            return parseBareEffect(start);
        }
        Ast.Expr expression = parseExpression();
        if (expression instanceof Call call) {
            return new Effect(call.name(), call.arguments(), pos(start));
        }
        return new Ast.ExpressionStmt(expression, pos(start));
    }

    /**
     * True when the token after a leading {@code IDENT} starts a new expression rather than
     * continuing one, which marks a parenthesis-free effect call.
     */
    private boolean followsBareArguments() {
        Token next = peekNext();
        return switch (next.type()) {
            case TEXT, NUMBER, GLOBAL_VAR, PLAYER_VAR, IDENT -> true;
            case KEYWORD -> next.isKeyword("true") || next.isKeyword("false")
                    || next.isKeyword("null") || next.isKeyword("not");
            default -> false;
        };
    }

    /** True when nothing follows on the line: a lone name is a zero-argument effect. */
    private boolean atEndOfLine() {
        Token next = peekNext();
        return next.type() == TokenType.NEWLINE || next.type() == TokenType.EOF
                || checkStructuralAt(next, "INDENT") || checkStructuralAt(next, "DEDENT");
    }

    private static boolean checkStructuralAt(Token token, String name) {
        return token.type() == TokenType.PUNCT && token.isPunct(name);
    }
    private Stmt parseBareEffect(Token name) {
        advance();
        List<Ast.Expr> arguments = new ArrayList<>();
        while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)
                && !checkStructural("INDENT") && !checkStructural("DEDENT")) {
            if (checkPunct(",")) {
                advance();
                continue;
            }
            arguments.add(parseExpression());
        }
        return new Effect(name.text(), List.copyOf(arguments), pos(name));
    }

    // ------------------------------------------------------------------ expressions

    private Ast.Expr parseExpression() {
        return parseTernary();
    }

    private Ast.Expr parseTernary() {
        Ast.Expr test = parseOr();
        if (checkPunct("?")) {
            Token mark = advance();
            Ast.Expr whenTrue = parseExpression();
            expectPunct(":");
            Ast.Expr whenFalse = parseExpression();
            return new Ternary(test, whenTrue, whenFalse, pos(mark));
        }
        return test;
    }

    private Ast.Expr parseOr() {
        Ast.Expr left = parseAnd();
        while (isKeyword("or") || checkOperator("||")) {
            Token operator = advance();
            left = new Ast.Binary("or", left, parseAnd(), pos(operator));
        }
        return left;
    }

    private Ast.Expr parseAnd() {
        Ast.Expr left = parseComparison();
        while (isKeyword("and") || checkOperator("&&")) {
            Token operator = advance();
            left = new Ast.Binary("and", left, parseComparison(), pos(operator));
        }
        return left;
    }

    private Ast.Expr parseComparison() {
        Ast.Expr left = parseAdditive();
        while (true) {
            Token token = peek();
            String operator = null;
            if (token.type() == TokenType.OPERATOR && COMPARISON_OPERATORS.contains(token.text())) {
                operator = token.text();
            } else if (token.isKeyword("contains") || token.isKeyword("is")) {
                operator = token.text();
            }
            if (operator == null) {
                return left;
            }
            advance();
            left = new Ast.Binary(operator, left, parseAdditive(), pos(token));
        }
    }

    private Ast.Expr parseAdditive() {
        Ast.Expr left = parseMultiplicative();
        while (check(TokenType.OPERATOR) && (checkOperator("+") || checkOperator("-")
                || checkOperator("<<<") || checkOperator(">>>"))) {
            Token operator = advance();
            left = new Ast.Binary(operator.text(), left, parseMultiplicative(), pos(operator));
        }
        return left;
    }

    private Ast.Expr parseMultiplicative() {
        Ast.Expr left = parseUnary();
        while (check(TokenType.OPERATOR) && (checkOperator("*") || checkOperator("/") || checkOperator("%"))) {
            Token operator = advance();
            left = new Ast.Binary(operator.text(), left, parseUnary(), pos(operator));
        }
        return left;
    }

    private Ast.Expr parseUnary() {
        Token token = peek();
        if (token.type() == TokenType.OPERATOR
                && (token.text().equals("-") || token.text().equals("!")
                        || token.text().equals("++") || token.text().equals("--"))) {
            advance();
            return new Ast.Unary(token.text(), parseUnary(), pos(token));
        }
        if (token.isKeyword("not")) {
            advance();
            return new Ast.Unary("!", parseUnary(), pos(token));
        }
        return parsePostfix();
    }

    private Ast.Expr parsePostfix() {
        Ast.Expr expression = parsePrimary();
        while (true) {
            Token token = peek();
            if (token.type() == TokenType.OPERATOR && token.isOperator(".")) {
                if (peekNext().type() != TokenType.IDENT) {
                    return expression;
                }
                advance();
                Token property = advance();
                expression = new Property(expression, property.text(), pos(token));
                continue;
            }
            if (checkPunct("[")) {
                advance();
                Ast.Expr key = parseExpression();
                expectPunct("]");
                expression = new Index(expression, key, pos(token));
                continue;
            }
            if (token.type() == TokenType.OPERATOR && (token.isOperator("++") || token.isOperator("--"))) {
                advance();
                expression = new Ast.Unary("post" + token.text(), expression, pos(token));
                continue;
            }
            return expression;
        }
    }

    private Ast.Expr parsePrimary() {
        Token token = peek();
        switch (token.type()) {
            case NUMBER -> {
                advance();
                return new Literal(token.number(), Ast.Type.NUMBER, pos(token));
            }
            case TEXT -> {
                advance();
                return new Literal(token.text(), Ast.Type.TEXT, pos(token));
            }
            case GLOBAL_VAR -> {
                advance();
                return new Global(token.text(), pos(token));
            }
            case PLAYER_VAR -> {
                advance();
                return new PlayerVar(token.text(), pos(token));
            }
            case KEYWORD -> {
                if (token.isKeyword("true") || token.isKeyword("false")) {
                    advance();
                    return new Literal(token.isKeyword("true"), Ast.Type.BOOL, pos(token));
                }
                if (token.isKeyword("null")) {
                    advance();
                    return new Literal(null, Ast.Type.NULL, pos(token));
                }
                throw error(token, "unexpected keyword '" + token.text() + "' in an expression");
            }
            case IDENT -> {
                advance();
                if (checkPunct("(")) {
                    advance();
                    List<Ast.Expr> arguments = new ArrayList<>();
                    while (!checkPunct(")")) {
                        arguments.add(parseExpression());
                        if (checkPunct(",")) {
                            advance();
                        } else {
                            break;
                        }
                    }
                    expectPunct(")");
                    return new Call(token.text(), List.copyOf(arguments), pos(token));
                }
                return new Local(token.text(), pos(token));
            }
            case PUNCT -> {
                if (token.isPunct("(")) {
                    advance();
                    Ast.Expr inner = parseExpression();
                    expectPunct(")");
                    return inner;
                }
                throw error(token, "unexpected '" + token.text() + "' in an expression");
            }
            default -> throw error(token, "unexpected " + token.describe() + " in an expression");
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Captures the raw text of the remaining expression on the line, used for trigger filters. */
    private String captureExpressionText() {
        StringBuilder builder = new StringBuilder();
        while (!check(TokenType.NEWLINE) && !check(TokenType.EOF) && !checkPunct(":")) {
            Token token = advance();
            boolean glue = token.type() == TokenType.PUNCT
                    || (token.type() == TokenType.OPERATOR && token.text().equals("."));
            if (builder.length() > 0 && !glue) {
                builder.append(' ');
            }
            // Re-emit source spelling: variable sigils and text quotes are not part of
            // the token text, but the filter is re-parsed, so they must be restored.
            if (token.type() == TokenType.GLOBAL_VAR) {
                builder.append('$');
            } else if (token.type() == TokenType.PLAYER_VAR) {
                builder.append('#');
            }
            if (token.type() == TokenType.TEXT) {
                builder.append('"').append(token.text().replace("\"", "\\\"")).append('"');
            } else {
                builder.append(token.text());
            }
        }
        return builder.toString();
    }

    private void skipNewlines() {
        while (check(TokenType.NEWLINE)) {
            advance();
        }
    }

    private Token peek() {
        return tokens.get(Math.min(position, tokens.size() - 1));
    }

    private Token peekNext() {
        return tokens.get(Math.min(position + 1, tokens.size() - 1));
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean checkPunct(String punct) {
        return check(TokenType.PUNCT) && peek().isPunct(punct);
    }

    private boolean checkOperator(String operator) {
        return peek().type() == TokenType.OPERATOR && peek().isOperator(operator);
    }

    private boolean checkStructural(String name) {
        return check(TokenType.PUNCT) && peek().isPunct(name);
    }

    private boolean isKeyword(String keyword) {
        return peek().isKeyword(keyword);
    }

    private Token advance() {
        Token token = peek();
        if (position < tokens.size() - 1) {
            position++;
        }
        return token;
    }

    private Token expect(TokenType type, String what) {
        if (!check(type)) {
            throw error(peek(), "expected " + what + ", found " + peek().describe());
        }
        return advance();
    }

    private Token expectKeyword(String keyword) {
        if (!peek().isKeyword(keyword)) {
            throw error(peek(), "expected '" + keyword + "', found " + peek().describe());
        }
        return advance();
    }

    private void expectPunct(String punct) {
        if (!checkPunct(punct)) {
            throw error(peek(), "expected '" + punct + "', found " + peek().describe());
        }
        advance();
    }

    private void expectStructural(String name, String what) {
        if (!checkStructural(name)) {
            throw error(peek(), "expected " + what);
        }
        advance();
    }

    /**
     * Command attributes accept quoted text or a bare token such as a permission node.
     * Permission nodes are dotted ({@code fastscript.heal}), so the segments are joined.
     */
    private String expectAttributeValue(String what) {
        if (!check(TokenType.TEXT) && !check(TokenType.IDENT)) {
            throw error(peek(), "expected " + what + ", found " + peek().describe());
        }
        StringBuilder builder = new StringBuilder(advance().text());
        while (checkOperator(".") && peekNext().type() == TokenType.IDENT) {
            advance();
            builder.append('.').append(advance().text());
        }
        return builder.toString();
    }

    private Pos pos(Token token) {
        return new Pos(token.line(), token.column());
    }

    private ScriptException error(Token token, String message) {
        return new ScriptException(message, fileName, token.line(), token.column());
    }
}
