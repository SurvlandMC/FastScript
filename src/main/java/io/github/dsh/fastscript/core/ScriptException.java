package io.github.dsh.fastscript.core;

/** Raised while loading or parsing a script; carries the source location for diagnostics. */
public class ScriptException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String source;
    private final int line;
    private final int column;

    public ScriptException(String message, String source, int line, int column) {
        super(message);
        this.source = source;
        this.line = line;
        this.column = column;
    }

    public ScriptException(String message, Throwable cause, String source, int line, int column) {
        super(message, cause);
        this.source = source;
        this.line = line;
        this.column = column;
    }

    public String source() {
        return source;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    /** Formats the failure as {@code file:line:col: message}, the way compilers report errors. */
    public String render() {
        String location = source == null ? "<script>" : source;
        return location + ":" + line + ":" + column + ": " + getMessage();
    }
}
