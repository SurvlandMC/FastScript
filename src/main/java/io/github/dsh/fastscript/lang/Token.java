package io.github.dsh.fastscript.lang;

/**
 * A single lexeme.
 *
 * @param type   token category
 * @param text   literal text (identifier/keyword/operator) or string payload for {@code TEXT}
 * @param number numeric payload for {@code NUMBER}, otherwise {@code 0}
 * @param line   1-based source line
 * @param column 1-based source column
 */
public record Token(TokenType type, String text, double number, int line, int column) {

    public boolean is(TokenType expected) {
        return type == expected;
    }

    public boolean isKeyword(String keyword) {
        return type == TokenType.KEYWORD && text.equals(keyword);
    }

    public boolean isOperator(String operator) {
        return type == TokenType.OPERATOR && text.equals(operator);
    }

    public boolean isPunct(String punct) {
        return type == TokenType.PUNCT && text.equals(punct);
    }

    public String describe() {
        return switch (type) {
            case NEWLINE -> "end of line";
            case EOF -> "end of file";
            case TEXT -> "text \"" + text + "\"";
            case NUMBER -> "number " + text;
            default -> "'" + text + "'";
        };
    }
}
