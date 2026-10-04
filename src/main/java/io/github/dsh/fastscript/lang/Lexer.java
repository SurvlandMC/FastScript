package io.github.dsh.fastscript.lang;

import io.github.dsh.fastscript.core.ScriptException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns script source into a token stream, emitting synthetic {@code INDENT}/{@code DEDENT}
 * tokens so the structural parser can work with explicit block boundaries.
 *
 * <p>Indentation inside brackets is ignored, which lets long calls be wrapped over several
 * lines; {@code #} starts a comment and a trailing backslash continues a line.</p>
 */
public final class Lexer {

    private static final Set<String> KEYWORDS = Set.of(
            "on", "if", "else", "while", "loop", "times", "for", "in", "break", "continue",
            "stop", "return", "function", "command", "true", "false", "null", "and", "or", "not",
            "contains", "is", "as", "let");

    private static final Map<String, Integer> OPERATOR_LENGTHS = Map.ofEntries(
            Map.entry(">>>", 3),
            Map.entry("<<<", 3),
            Map.entry("==", 2), Map.entry("!=", 2), Map.entry(">=", 2), Map.entry("<=", 2),
            Map.entry("+=", 2), Map.entry("-=", 2), Map.entry("*=", 2), Map.entry("/=", 2),
            Map.entry("%=", 2), Map.entry("++", 2), Map.entry("--", 2), Map.entry("&&", 2),
            Map.entry("||", 2),
            Map.entry("+", 1), Map.entry("-", 1), Map.entry("*", 1), Map.entry("/", 1),
            Map.entry("%", 1), Map.entry(">", 1), Map.entry("<", 1), Map.entry("=", 1),
            Map.entry("!", 1), Map.entry(".", 1));

    private static final String PUNCTUATION = "(){},:[]";

    private final String source;
    private final String fileName;
    private final List<Token> tokens = new ArrayList<>();
    private final Deque<Integer> indents = new ArrayDeque<>();
    private int position;
    private int line = 1;
    private int column = 1;
    private boolean lineStart = true;
    /** True once a real token has been produced on the current line. */
    private boolean lineHasContent;
    private int bracketDepth;

    public Lexer(String source, String fileName) {
        String normalised = source.replace("\r\n", "\n").replace('\r', '\n');
        // Editors on Windows often save a byte-order mark; strip it so scripts load unchanged.
        if (!normalised.isEmpty() && normalised.charAt(0) == '\uFEFF') {
            normalised = normalised.substring(1);
        }
        this.source = normalised;
        this.fileName = fileName;
        this.indents.push(0);
    }

    public List<Token> tokenize() {
        while (!atEnd()) {
            if (lineStart && bracketDepth == 0) {
                handleIndentation();
                if (atEnd()) {
                    break;
                }
            }
            int tokenLine = line;
            int tokenColumn = column;
            char current = peek();
            if (current == ' ' || current == '\t') {
                advance();
                continue;
            }
            // '#' doubles as the comment marker and the player-variable prefix: '#name'
            // (hash immediately followed by a name) is a variable, anything else is a comment.
            if (current == '#' && !isIdentifierStart(peekNext())) {
                if (lineHasContent) {
                    // A trailing comment ends the statement just like a newline would; without
                    // this the next statement would be treated as a continuation.
                    skipToEndOfLine();
                    add(TokenType.NEWLINE, "\\n", 0);
                    lineHasContent = false;
                    continue;
                }
                // A comment-only line is ignored completely, including its line break.
                skipToEndOfLine();
                if (!atEnd() && peek() == '\n') {
                    advance();
                    lineStart = true;
                }
                continue;
            }
            if (current == '\\' && peekNext() == '\n') {
                advance();
                advance();
                continue;
            }
            if (current == '\n') {
                advance();
                add(TokenType.NEWLINE, "\\n", 0);
                lineStart = true;
                continue;
            }
            if (current == '"') {
                readText(tokenLine, tokenColumn);
                continue;
            }
            if (Character.isDigit(current)
                    || (current == '.' && Character.isDigit(peekNext()))) {
                readNumber(tokenLine, tokenColumn);
                continue;
            }
            if (current == '$' || current == '#') {
                readVariable(current == '$' ? TokenType.GLOBAL_VAR : TokenType.PLAYER_VAR,
                        tokenLine, tokenColumn);
                continue;
            }
            if (isIdentifierStart(current)) {
                readIdentifierOrKeyword(tokenLine, tokenColumn);
                continue;
            }
            if (PUNCTUATION.indexOf(current) >= 0) {
                advance();
                if (current == '(' || current == '[' || current == '{') {
                    bracketDepth++;
                } else if (current == ')' || current == ']' || current == '}') {
                    bracketDepth = Math.max(0, bracketDepth - 1);
                }
                add(TokenType.PUNCT, String.valueOf(current), 0, tokenLine, tokenColumn);
                continue;
            }
            if (readOperator(tokenLine, tokenColumn)) {
                continue;
            }
            throw error("unexpected character '" + current + "'");
        }

        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).type() != TokenType.NEWLINE) {
            add(TokenType.NEWLINE, "\\n", 0);
        }
        while (indents.size() > 1) {
            indents.pop();
            add(TokenType.PUNCT, "DEDENT", 0);
        }
        add(TokenType.EOF, "", 0);
        return List.copyOf(tokens);
    }

    // ------------------------------------------------------------------ pieces

    /** @return true when the caller should re-inspect the same position */
    private boolean handleIndentation() {
        int width = 0;
        while (!atEnd()) {
            char current = peek();
            if (current == ' ') {
                width++;
                advance();
            } else if (current == '\t') {
                width += 4;
                advance();
            } else {
                break;
            }
        }
        // A line starting with '#name' is code (a player variable), not a comment;
        // only '#' not followed by a name is skipped here like a blank line.
        if (atEnd() || peek() == '\n' || (peek() == '#' && !isIdentifierStart(peekNext()))) {
            skipToEndOfLine();
            return true;
        }
        lineStart = false;
        int current = indents.peek();
        if (width > current) {
            indents.push(width);
            add(TokenType.PUNCT, "INDENT", 0);
        } else if (width < current) {
            while (indents.size() > 1 && width < indents.peek()) {
                indents.pop();
                add(TokenType.PUNCT, "DEDENT", 0);
            }
            if (indents.peek() != width) {
                throw error("inconsistent indentation");
            }
        }
        return true;
    }

    private void skipToEndOfLine() {
        while (!atEnd() && peek() != '\n') {
            advance();
        }
    }

    private void readText(int startLine, int startColumn) {
        advance();
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw new ScriptException("unterminated text literal", fileName, startLine, startColumn);
            }
            char current = peek();
            if (current == '\\') {
                advance();
                if (atEnd()) {
                    throw new ScriptException("unterminated text literal", fileName, startLine, startColumn);
                }
                char escaped = advance();
                builder.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> escaped;
                });
                continue;
            }
            if (current == '"') {
                advance();
                break;
            }
            builder.append(advance());
        }
        add(TokenType.TEXT, builder.toString(), 0, startLine, startColumn);
    }

    private void readNumber(int startLine, int startColumn) {
        int start = position;
        while (!atEnd() && (Character.isDigit(peek()) || peek() == '.')) {
            advance();
        }
        String text = source.substring(start, position);
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException error) {
            throw new ScriptException("malformed number '" + text + "'", fileName, startLine, startColumn);
        }
        add(TokenType.NUMBER, text, value, startLine, startColumn);
    }

    private void readVariable(TokenType type, int startLine, int startColumn) {
        char marker = advance();
        if (atEnd() || !isIdentifierStart(peek())) {
            throw new ScriptException("expected a variable name after '" + marker + "'",
                    fileName, startLine, startColumn);
        }
        int start = position;
        while (!atEnd() && isIdentifierPart(peek())) {
            advance();
        }
        add(type, source.substring(start, position), 0, startLine, startColumn);
    }

    private void readIdentifierOrKeyword(int startLine, int startColumn) {
        int start = position;
        while (!atEnd() && isIdentifierPart(peek())) {
            advance();
        }
        String text = source.substring(start, position);
        add(KEYWORDS.contains(text) ? TokenType.KEYWORD : TokenType.IDENT, text, 0, startLine, startColumn);
    }

    private boolean readOperator(int startLine, int startColumn) {
        for (int length = 3; length >= 1; length--) {
            if (position + length > source.length()) {
                continue;
            }
            String candidate = source.substring(position, position + length);
            Integer expected = OPERATOR_LENGTHS.get(candidate);
            if (expected == null || expected != length) {
                continue;
            }
            if (candidate.equals("!") && peekNext() == '=') {
                continue;
            }
            for (int i = 0; i < length; i++) {
                advance();
            }
            add(TokenType.OPERATOR, candidate, 0, startLine, startColumn);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ plumbing

    private void add(TokenType type, String text, double number) {
        add(type, text, number, line, column);
    }

    private void add(TokenType type, String text, double number, int tokenLine, int tokenColumn) {
        tokens.add(new Token(type, text, number, tokenLine, tokenColumn));
        // Any real token means the current line has code on it; a line break ends it.
        // Trailing-comment handling relies on this to decide whether '#' ends a
        // statement (emit NEWLINE) or is a comment-only line (skip silently).
        lineHasContent = type != TokenType.NEWLINE;
    }

    private boolean atEnd() {
        return position >= source.length();
    }

    private char peek() {
        return source.charAt(position);
    }

    private char peekNext() {
        return position + 1 < source.length() ? source.charAt(position + 1) : '\0';
    }

    private char advance() {
        char current = source.charAt(position++);
        if (current == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return current;
    }

    private ScriptException error(String message) {
        return new ScriptException(message, fileName, line, column);
    }

    private static boolean isIdentifierStart(char character) {
        return Character.isLetter(character) || character == '_';
    }

    private static boolean isIdentifierPart(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }
}
