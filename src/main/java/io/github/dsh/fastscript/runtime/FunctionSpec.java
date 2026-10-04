package io.github.dsh.fastscript.runtime;

/**
 * A script function callable from other handlers in the same script.
 *
 * @param name       script-level function name
 * @param methodName generated static method to invoke
 * @param descriptor JVM descriptor of the generated method
 * @param arity      number of declared parameters
 */
public record FunctionSpec(String name, String methodName, String descriptor, int arity) {

    public static FunctionSpec of(String name, String methodName, String descriptor, int arity) {
        return new FunctionSpec(name, methodName, descriptor, arity);
    }
}
