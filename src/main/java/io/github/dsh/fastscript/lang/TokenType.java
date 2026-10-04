package io.github.dsh.fastscript.lang;

/** Token categories produced by {@link Lexer}. */
public enum TokenType {
    IDENT,
    NUMBER,
    TEXT,
    /** {@code $name} — global or runtime variable. */
    GLOBAL_VAR,
    /** {@code #name} — player-scoped variable. */
    PLAYER_VAR,
    /** {@code loop-index} and friends resolve as ordinary identifiers. */
    KEYWORD,
    PUNCT,
    OPERATOR,
    NEWLINE,
    EOF
}
