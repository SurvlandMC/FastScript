package io.github.dsh.fastscript.runtime;

/**
 * One script file to compile.
 *
 * @param id         stable identifier derived from the relative file path
 * @param fileName   base file name used in diagnostics, for example {@code heal.fs}
 * @param source     script text
 * @param origin     absolute path on disk, or a synthetic marker for built-in scripts
 */
public record ScriptSource(String id, String fileName, String source, String origin) {

    public ScriptSource(String id, String fileName, String source) {
        this(id, fileName, source, fileName);
    }
}
