package io.github.dsh.fastscript.core;

/** Raised when a script statement cannot be executed (bad types, missing target, and so on). */
public class EvalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EvalException(String message) {
        super(message);
    }

    public EvalException(String message, Throwable cause) {
        super(message, cause);
    }
}
