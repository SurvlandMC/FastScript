package io.github.dsh.fastscript.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal dependency-free test framework: the project builds without Maven or
 * Gradle, so the suite is plain Java compiled and run by {@code test.ps1}.
 */
public final class Suite {

    private int passed;
    private final List<String> failures = new ArrayList<>();

    /** Functional interface allowing checked throwables in test bodies. */
    @FunctionalInterface
    public interface Throwing {
        void run() throws Throwable;
    }

    public void check(boolean condition, String name) {
        if (condition) {
            passed++;
            System.out.println("  ok - " + name);
        } else {
            failures.add(name);
            System.out.println("  FAIL - " + name);
        }
    }

    /** Equality that treats any two {@link Number}s by value. */
    public void eq(Object expected, Object actual, String name) {
        boolean ok;
        if (expected instanceof Number a && actual instanceof Number b) {
            ok = a.doubleValue() == b.doubleValue();
        } else {
            ok = Objects.equals(expected, actual);
        }
        check(ok, ok ? name : name + " (expected=" + expected + ", actual=" + actual + ")");
    }

    /** Expects {@code body} to throw {@code type}, unwrapping cause chains. */
    public void fails(Class<? extends Throwable> type, Throwing body, String name) {
        try {
            body.run();
            check(false, name + " (no exception thrown)");
        } catch (Throwable thrown) {
            boolean match = false;
            for (Throwable current = thrown; current != null; current = current.getCause()) {
                if (type.isInstance(current)) {
                    match = true;
                    break;
                }
            }
            check(match, match ? name
                    : name + " (wrong exception: " + thrown.getClass().getName() + ": " + thrown.getMessage() + ")");
        }
    }

    /** Expects a failure whose message chain contains {@code fragment}. */
    public void failsMsg(String fragment, Throwing body, String name) {
        try {
            body.run();
            check(false, name + " (no exception thrown)");
        } catch (Throwable thrown) {
            boolean match = false;
            for (Throwable current = thrown; current != null; current = current.getCause()) {
                if (current.getMessage() != null && current.getMessage().contains(fragment)) {
                    match = true;
                    break;
                }
            }
            check(match, match ? name : name + " (message mismatch: " + thrown.getMessage() + ")");
        }
    }

    public int passed() {
        return passed;
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }
}
