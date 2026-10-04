package io.github.dsh.fastscript.runtime;

import java.util.List;

/**
 * A command registered by a compiled script.
 *
 * @param name       command label without the leading slash
 * @param permission required permission, empty when the command is public
 * @param methodName generated static method to invoke
 * @param arguments  comma separated argument names in declaration order
 */
public record CommandSpec(String name, String permission, String methodName, String arguments) {

    public static CommandSpec of(String name, String permission, String methodName, String arguments) {
        return new CommandSpec(name, permission, methodName, arguments);
    }

    public List<String> argumentNames() {
        if (arguments == null || arguments.isBlank()) {
            return List.of();
        }
        return List.of(arguments.split(","));
    }

    public int arity() {
        return argumentNames().size();
    }
}
