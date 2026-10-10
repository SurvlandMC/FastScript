package io.github.dsh.fastscript.tests;

import io.github.dsh.fastscript.core.ScriptException;
import io.github.dsh.fastscript.lang.Lexer;
import io.github.dsh.fastscript.lang.Token;
import io.github.dsh.fastscript.lang.TokenType;
import java.util.List;
import java.util.stream.Collectors;

/** Tokenizer tests: token shapes, comments, indentation, escapes, errors. */
public final class LexerTest {

    private LexerTest() {
    }

    /** Renders a token stream as "TYPE:text" entries, keeping NEWLINE, dropping EOF. */
    static List<String> sig(String source) {
        return new Lexer(source, "test.fs").tokenize().stream()
                .filter(token -> token.type() != TokenType.EOF)
                .map(token -> token.type() + ":" + token.text())
                .collect(Collectors.toList());
    }

    /** Same as {@link #sig(String)} but without NEWLINE entries. */
    static List<String> code(String source) {
        return sig(source).stream()
                .filter(x -> !x.startsWith("NEWLINE"))
                .collect(Collectors.toList());
    }

    public static void run(Suite s) {
        s.eq(List.of("KEYWORD:on", "IDENT:player", "IDENT:join", "PUNCT::", "NEWLINE:\\n"),
                sig("on player join:"), "lexer.header");
        // '-' is an identifier character, so loop-index is one token.
        s.eq(List.of("IDENT:loop-index"), code("loop-index"), "lexer.dashed-ident");
        // '?' is punctuation, multi-char operators lex greedily.
        s.eq(List.of("IDENT:a", "PUNCT:?", "IDENT:b", "PUNCT::", "IDENT:c"),
                code("a ? b : c"), "lexer.ternary-punct");
        s.eq(List.of("IDENT:a", "OPERATOR:==", "IDENT:b", "OPERATOR:!=",
                        "IDENT:c", "OPERATOR:>=", "IDENT:d", "OPERATOR:<<<", "IDENT:e"),
                code("a == b != c >= d <<< e"), "lexer.operators");
        s.eq(List.of("IDENT:x", "OPERATOR:++"), code("x++"), "lexer.increment");

        s.eq(12.0, new Lexer("12", "t.fs").tokenize().get(0).number(), "lexer.int-value");
        s.eq(3.5, new Lexer("3.5", "t.fs").tokenize().get(0).number(), "lexer.float-value");
        s.eq(0.5, new Lexer(".5", "t.fs").tokenize().get(0).number(), "lexer.leading-dot");
        s.eq(List.of("NUMBER:12", "NUMBER:3.5", "NUMBER:.5"),
                code("12 3.5 .5"), "lexer.number-spelling");

        s.eq("a\nb\t\"c\"\\", new Lexer("\"a\\nb\\t\\\"c\\\"\\\\\"", "t.fs").tokenize().get(0).text(),
                "lexer.escapes");
        s.eq("aqb", new Lexer("\"a\\qb\"", "t.fs").tokenize().get(0).text(),
                "lexer.unknown-escape-passthrough");

        // A comment-only line leaves no code tokens behind.
        s.eq(List.of("NEWLINE:\\n", "IDENT:log", "TEXT:x", "NEWLINE:\\n"),
                sig("# hello\nlog \"x\"\n"), "lexer.comment-only-line");
        // A trailing comment terminates the statement like a newline. The comment
        // itself emits one NEWLINE and the line break another; the parser folds
        // blank lines when it builds blocks.
        s.eq(List.of("IDENT:log", "TEXT:x", "NEWLINE:\\n", "NEWLINE:\\n",
                        "IDENT:log", "TEXT:y", "NEWLINE:\\n"),
                sig("log \"x\" # done\nlog \"y\"\n"), "lexer.trailing-comment");
        // '#name' with no space is a player variable, not a comment.
        s.eq(List.of("PLAYER_VAR:visits", "OPERATOR:=", "NUMBER:1"),
                code("#visits = 1"), "lexer.player-var");
        s.eq(List.of("NEWLINE:\\n"), sig("# visits\n"), "lexer.hash-space-is-comment");
        s.eq(List.of("GLOBAL_VAR:g"), code("$g"), "lexer.global-var");

        s.eq(List.of("KEYWORD:on", "IDENT:join", "PUNCT::", "NEWLINE:\\n",
                        "PUNCT:INDENT", "IDENT:log", "TEXT:x", "NEWLINE:\\n", "PUNCT:DEDENT"),
                sig("on join:\n    log \"x\"\n"), "lexer.indent-dedent");

        s.eq(List.of("IDENT:log", "TEXT:x", "NEWLINE:\\n"),
                sig("log \\\n\"x\"\n"), "lexer.line-continuation");

        // U+FEFF built programmatically so no invisible bytes hide in sources.
        String bom = Character.toString((char) 0xFEFF) + "log \"x\"\n";
        s.eq("IDENT", new Lexer(bom, "t.fs").tokenize().get(0).type().name(),
                "lexer.bom-stripped");
        s.eq(List.of("IDENT:log", "TEXT:x", "NEWLINE:\\n"),
                sig("log \"x\"\r\n"), "lexer.crlf-normalized");

        s.fails(ScriptException.class, () -> sig("@"), "lexer.unexpected-character");
        s.fails(ScriptException.class, () -> sig("\"abc"), "lexer.unterminated-text");
        s.fails(ScriptException.class, () -> sig("1.2.3"), "lexer.malformed-number");
        s.fails(ScriptException.class, () -> sig("$\n"), "lexer.dangling-dollar");
    }
}
