package io.github.dsh.fastscript.tests;

import io.github.dsh.fastscript.bench.ParityTest;

/**
 * Entry point for the dependency-free test suite, run by {@code test.ps1}.
 * Exits non-zero when any check fails.
 */
public final class AllTests {

    private AllTests() {
    }

    public static void main(String[] args) {
        Suite suite = new Suite();
        run("lexer", suite, LexerTest::run);
        run("parser", suite, ParserTest::run);
        run("values", suite, ValuesTest::run);
        run("scripts", suite, ScriptTest::run);
        run("parity", suite, ParityTest::run);
        System.out.println();
        System.out.println("passed: " + suite.passed() + ", failed: " + suite.failures().size());
        for (String failure : suite.failures()) {
            System.out.println("FAILED: " + failure);
        }
        if (!suite.failures().isEmpty()) {
            System.exit(1);
        }
        System.out.println("ALL TESTS PASSED");
    }

    private static void run(String name, Suite suite, java.util.function.Consumer<Suite> body) {
        System.out.println("== " + name + " ==");
        try {
            body.accept(suite);
        } catch (Throwable thrown) {
            suite.check(false, name + " crashed: " + thrown);
            thrown.printStackTrace(System.out);
        }
    }
}
