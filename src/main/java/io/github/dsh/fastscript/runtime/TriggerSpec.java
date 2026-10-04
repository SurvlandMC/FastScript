package io.github.dsh.fastscript.runtime;

/**
 * A handler registered by a compiled script.
 *
 * @param eventName  normalised event name from the script header
 * @param filter     optional {@code where} condition text
 * @param methodName generated static method to invoke
 */
public record TriggerSpec(String eventName, String filter, String methodName) {

    public static TriggerSpec of(String eventName, String filter, String methodName) {
        return new TriggerSpec(normalise(eventName), filter, methodName);
    }

    /**
     * Canonicalises a script event header so dispatch can use exact map lookups.
     *
     * <p>Mirrors the aliasing in {@code ScriptEvents}: case and extra whitespace are
     * ignored, a leading {@code player} and a trailing {@code event} are stripped, so
     * {@code player join}, {@code join event} and {@code JOIN} all become {@code join}.</p>
     */
    static String normalise(String raw) {
        String name = raw.toLowerCase(java.util.Locale.ROOT).trim().replaceAll("\\s+", " ");
        if (name.startsWith("player ")) {
            name = name.substring("player ".length());
        }
        if (name.endsWith(" event")) {
            name = name.substring(0, name.length() - " event".length()).trim();
        }
        return name;
    }

    public boolean hasFilter() {
        return filter != null && !filter.isBlank();
    }
}
