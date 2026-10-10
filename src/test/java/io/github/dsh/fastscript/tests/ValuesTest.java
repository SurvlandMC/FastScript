package io.github.dsh.fastscript.tests;

import io.github.dsh.fastscript.core.EvalException;
import io.github.dsh.fastscript.core.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Unit tests for value semantics: coercions, arithmetic, text, containers. */
public final class ValuesTest {

    private ValuesTest() {
    }

    public static void run(Suite s) {
        // --- coercions ------------------------------------------------------
        s.eq(5.0, Values.toNumber(5), "values.to-number-int");
        s.eq(3.5, Values.toNumber("3.5"), "values.to-number-text");
        s.eq(2.0, Values.toNumber("  2 "), "values.to-number-trimmed");
        s.eq(1.0, Values.toNumber(true), "values.to-number-bool");
        s.failsMsg("cannot use null as a number",
                () -> Values.toNumber(null), "values.to-number-null");
        s.failsMsg("cannot use an empty text as a number",
                () -> Values.toNumber(""), "values.to-number-empty");
        s.failsMsg("cannot use text 'abc' as a number",
                () -> Values.toNumber("abc"), "values.to-number-garbage");
        s.eq(2L, Values.toLong(2.9), "values.to-long-truncates");
        s.eq(7, Values.toInt("7"), "values.to-int-text");

        s.eq(true, Values.toBool(true), "values.bool-bool");
        s.eq(false, Values.toBool(0.0), "values.bool-zero");
        s.eq(true, Values.toBool(2.5), "values.bool-number");
        s.eq(false, Values.toBool(""), "values.bool-empty-text");
        s.eq(false, Values.toBool("FALSE"), "values.bool-false-text");
        s.eq(true, Values.toBool("x"), "values.bool-text");
        s.eq(false, Values.toBool(null), "values.bool-null");
        s.eq(true, Values.toBool(List.of()), "values.bool-list");

        s.eq("", Values.toText(null), "values.text-null");
        s.eq("5", Values.toText(5.0), "values.text-integral");
        s.eq("2.5", Values.toText(2.5), "values.text-fraction");
        s.eq("true", Values.toText(true), "values.text-bool");
        s.eq("[1, a]", Values.toText(new ArrayList<>(List.of(1.0, "a"))), "values.text-list");
        s.eq("5", Values.format(5.0), "values.format-integral");
        s.eq("2.5", Values.format(2.5), "values.format-fraction");
        s.eq("1.0E16", Values.format(1e16), "values.format-huge");

        s.eq("null", Values.typeName(null), "values.type-null");
        s.eq("number", Values.typeName(1), "values.type-integer");
        s.eq("text", Values.typeName("s"), "values.type-text");
        s.eq("boolean", Values.typeName(false), "values.type-bool");
        s.eq("list", Values.typeName(List.of()), "values.type-list");
        s.eq("map", Values.typeName(Map.of()), "values.type-map");

        // --- equality -------------------------------------------------------
        s.eq(true, Values.looseEquals(null, null), "values.eq-null-null");
        s.eq(false, Values.looseEquals(null, 1.0), "values.eq-null-value");
        s.eq(true, Values.looseEquals(1, 1.0), "values.eq-int-double");
        s.eq(true, Values.looseEquals("1", 1.0), "values.eq-text-number");
        s.eq(false, Values.looseEquals("a", "b"), "values.eq-texts");
        s.eq(false, Values.notEquals(1.0, 1.0), "values.not-eq");

        // --- arithmetic -----------------------------------------------------
        s.eq(3.0, Values.add(1.0, 2.0), "values.add-numbers");
        s.eq("a1", Values.add("a", 1.0), "values.add-concat");
        s.eq("ab", Values.add("a", "b"), "values.add-texts");
        s.eq(6.0, Values.subtract(10.0, 4.0), "values.subtract");
        s.eq(7.5, Values.multiply(3.0, 2.5), "values.multiply");
        s.eq(3.5, Values.divide(7.0, 2.0), "values.divide");
        s.failsMsg("division by zero", () -> Values.divide(1.0, 0.0), "values.divide-zero");
        s.eq(1.0, Values.modulo(7.0, 3.0), "values.modulo");
        s.failsMsg("division by zero in modulo",
                () -> Values.modulo(1.0, 0.0), "values.modulo-zero");

        s.eq(true, Values.less(1.0, 2.0), "values.less");
        s.eq(true, Values.greaterOrEqual(2.0, 2.0), "values.greater-or-equal");
        s.eq(true, Values.lessOrEqual(2.0, 2.0), "values.less-or-equal");
        // Non-numbers compare as text: "10" sorts before "9".
        // Non-numbers compare as text: "10" sorts before "9", "2" after "10".
        s.eq(true, Values.less("10", "9"), "values.text-compare");
        s.eq(true, Values.greater(2.0, "10"), "values.mixed-compare");

        // --- membership, size, text -----------------------------------------
        s.eq(true, Values.contains(List.of(1.0, 2.0), 2.0), "values.contains-list");
        s.eq(false, Values.contains(List.of(1.0), 9.0), "values.contains-list-miss");
        s.eq(true, Values.contains(Map.of("k", 1), "k"), "values.contains-map");
        s.eq(true, Values.contains("hello", "ell"), "values.contains-text");
        s.eq(false, Values.contains(null, "x"), "values.contains-null");

        s.eq(3, Values.size(List.of(1, 2, 3)), "values.size-list");
        s.eq(2, Values.size(Map.of("a", 1, "b", 2)), "values.size-map");
        s.eq(5, Values.size("hello"), "values.size-text");
        s.failsMsg("cannot take the size of number",
                () -> Values.size(42.0), "values.size-number");

        s.eq("HI", Values.upper("hi"), "values.upper");
        s.eq("hi", Values.lower("HI"), "values.lower");
        s.eq("a", Values.trimmed("  a  "), "values.trimmed");
        s.eq("bbb", Values.replace("aaa", "a", "b"), "values.replace");
        s.eq("el", Values.substring("hello", 1, 3), "values.substring");
        s.eq("hi", Values.substring("hi", -5, 99), "values.substring-clamp");
        s.eq(List.of("a", "b"), Values.split("a,b", ","), "values.split");
        s.eq(List.of("a", "b", "c"), Values.split("abc", ""), "values.split-chars");
        s.eq("a-b", Values.join(new ArrayList<>(List.of("a", "b")), "-"), "values.join");
        s.eq("x", Values.join("x", "-"), "values.join-non-list");
        s.eq("12", Values.concat(1.0, 2.0), "values.concat");

        // --- index ----------------------------------------------------------
        List<Object> list = new ArrayList<>(List.of("a", "b"));
        s.eq("b", Values.index(list, 1.0), "values.index-list");
        s.eq(null, Values.index(list, 9.0), "values.index-oob");
        s.eq(null, Values.index(list, -1.0), "values.index-negative");
        s.eq(null, Values.index(null, 0.0), "values.index-null");
        s.eq(1, Values.index(Map.of("k", 1), "k"), "values.index-map");
        s.eq("e", Values.index("hello", 1.0), "values.index-string");
        s.eq(null, Values.index("hi", 9.0), "values.index-string-oob");
        s.failsMsg("cannot index into number",
                () -> Values.index(1.0, 0.0), "values.index-number");

        List<Object> original = new ArrayList<>(List.of("a"));
        List<Object> copy = Values.toList(original);
        s.check(copy.equals(original) && copy != original, "values.tolist-copy");
        s.eq(List.of(), Values.toList(42.0), "values.tolist-non-list");
    }
}
