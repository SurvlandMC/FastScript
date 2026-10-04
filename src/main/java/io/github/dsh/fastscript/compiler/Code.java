package io.github.dsh.fastscript.compiler;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Thin typed facade over ASM's {@link MethodVisitor}.
 *
 * <p>{@code InstructionAdapter} is avoided on purpose: its overloads take {@link Type} objects,
 * which makes slot-indexed load/store calls awkward. Every method here works with plain
 * descriptors, so the compiler can reason about representations directly.</p>
 */
final class Code {

    static final String OBJECT = "Ljava/lang/Object;";
    static final String STRING = "Ljava/lang/String;";
    static final String DOUBLE = "D";
    static final String BOOL = "Z";
    static final String LIST = "Ljava/util/List;";
    static final String INTEGER = "Ljava/lang/Integer;";
    static final String DOUBLE_BOX = "Ljava/lang/Double;";
    static final String BOOLEAN_BOX = "Ljava/lang/Boolean;";
    static final String LONG_BOX = "Ljava/lang/Long;";

    private final MethodVisitor method;

    Code(MethodVisitor method) {
        this.method = method;
    }

    MethodVisitor raw() {
        return method;
    }

    // ------------------------------------------------------------------ constants

    void dconst(double value) {
        if (value == 0.0) {
            method.visitInsn(Opcodes.DCONST_0);
        } else if (value == 1.0) {
            method.visitInsn(Opcodes.DCONST_1);
        } else {
            method.visitLdcInsn(value);
        }
    }

    void iconst(int value) {
        switch (value) {
            case -1 -> method.visitInsn(Opcodes.ICONST_M1);
            case 0 -> method.visitInsn(Opcodes.ICONST_0);
            case 1 -> method.visitInsn(Opcodes.ICONST_1);
            case 2 -> method.visitInsn(Opcodes.ICONST_2);
            case 3 -> method.visitInsn(Opcodes.ICONST_3);
            case 4 -> method.visitInsn(Opcodes.ICONST_4);
            case 5 -> method.visitInsn(Opcodes.ICONST_5);
            default -> {
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                    method.visitIntInsn(Opcodes.BIPUSH, value);
                } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                    method.visitIntInsn(Opcodes.SIPUSH, value);
                } else {
                    method.visitLdcInsn(value);
                }
            }
        }
    }

    void aconst(String value) {
        if (value == null) {
            method.visitInsn(Opcodes.ACONST_NULL);
        } else {
            method.visitLdcInsn(value);
        }
    }

    // ------------------------------------------------------------------ variables

    void load(int slot, String type) {
        method.visitVarInsn(loadOpcode(type), slot);
    }

    void store(int slot, String type) {
        method.visitVarInsn(storeOpcode(type), slot);
    }

    void loadDouble(int slot) {
        method.visitVarInsn(Opcodes.DLOAD, slot);
    }

    void storeDouble(int slot) {
        method.visitVarInsn(Opcodes.DSTORE, slot);
    }

    void loadRef(int slot) {
        method.visitVarInsn(Opcodes.ALOAD, slot);
    }

    void storeRef(int slot) {
        method.visitVarInsn(Opcodes.ASTORE, slot);
    }

    void loadInt(int slot) {
        method.visitVarInsn(Opcodes.ILOAD, slot);
    }

    void storeInt(int slot) {
        method.visitVarInsn(Opcodes.ISTORE, slot);
    }

    private static int loadOpcode(String type) {
        return switch (type) {
            case DOUBLE -> Opcodes.DLOAD;
            case "J" -> Opcodes.LLOAD;
            case BOOL, "I", "B", "C", "S" -> Opcodes.ILOAD;
            default -> Opcodes.ALOAD;
        };
    }

    private static int storeOpcode(String type) {
        return switch (type) {
            case DOUBLE -> Opcodes.DSTORE;
            case "J" -> Opcodes.LSTORE;
            case BOOL, "I", "B", "C", "S" -> Opcodes.ISTORE;
            default -> Opcodes.ASTORE;
        };
    }

    // ------------------------------------------------------------------ stack

    void pop() {
        method.visitInsn(Opcodes.POP);
    }

    void pop2() {
        method.visitInsn(Opcodes.POP2);
    }

    void swap() {
        method.visitInsn(Opcodes.SWAP);
    }

    void dup() {
        method.visitInsn(Opcodes.DUP);
    }

    void dup2() {
        method.visitInsn(Opcodes.DUP2);
    }

    void dcmpg() {
        method.visitInsn(Opcodes.DCMPG);
    }

    void dcmpl() {
        method.visitInsn(Opcodes.DCMPL);
    }

    void dneg() {
        method.visitInsn(Opcodes.DNEG);
    }

    void dadd() {
        method.visitInsn(Opcodes.DADD);
    }

    void dsub() {
        method.visitInsn(Opcodes.DSUB);
    }

    void dmul() {
        method.visitInsn(Opcodes.DMUL);
    }

    void ddiv() {
        method.visitInsn(Opcodes.DDIV);
    }

    void drem() {
        method.visitInsn(Opcodes.DREM);
    }

    void ixor() {
        method.visitInsn(Opcodes.IXOR);
    }

    void i2d() {
        method.visitInsn(Opcodes.I2D);
    }

    void d2i() {
        method.visitInsn(Opcodes.D2I);
    }

    void returnValue(String type) {
        method.visitInsn(switch (type) {
            case DOUBLE -> Opcodes.DRETURN;
            case BOOL, "I" -> Opcodes.IRETURN;
            case "V" -> Opcodes.RETURN;
            default -> Opcodes.ARETURN;
        });
    }

    // ------------------------------------------------------------------ branches

    void jump(int opcode, Label target) {
        method.visitJumpInsn(opcode, target);
    }

    void label(Label label) {
        method.visitLabel(label);
    }

    void lineNumber(int line, Label start) {
        method.visitLineNumber(line, start);
    }

    // ------------------------------------------------------------------ arrays and calls

    void newObjectArray(int length) {
        iconst(length);
        method.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
    }

    void newList() {
        method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
    }

    void aastore() {
        method.visitInsn(Opcodes.AASTORE);
    }

    void aaload() {
        method.visitInsn(Opcodes.AALOAD);
    }

    void invokeStatic(String owner, String name, String descriptor) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, descriptor, false);
    }

    /** Required for constructors and private members; {@code INVOKEVIRTUAL} would not verify. */
    void invokeSpecial(String owner, String name, String descriptor) {
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, descriptor, false);
    }

    void invokeVirtual(String owner, String name, String descriptor) {
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false);
    }

    void invokeInterface(String owner, String name, String descriptor) {
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, descriptor, true);
    }

    void checkCast(String internalName) {
        method.visitTypeInsn(Opcodes.CHECKCAST, internalName);
    }

    void instanceOf(String internalName) {
        method.visitTypeInsn(Opcodes.INSTANCEOF, internalName);
    }

    void newInstance(String internalName) {
        method.visitTypeInsn(Opcodes.NEW, internalName);
    }

    // ------------------------------------------------------------------ conversions

    void box(String descriptor) {
        switch (descriptor) {
            case DOUBLE -> invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
            case BOOL -> invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
            case "I" -> invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
            case "J" -> invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
            case OBJECT, STRING, LIST -> {
                // Already a reference: nothing to box.
            }
            default -> throw new IllegalStateException("cannot box " + descriptor);
        }
    }

    void unbox(String descriptor) {
        switch (descriptor) {
            case DOUBLE -> invokeStatic(VALUES_OWNER, "toNumber", "(Ljava/lang/Object;)D");
            case BOOL -> invokeStatic(VALUES_OWNER, "toBool", "(Ljava/lang/Object;)Z");
            case STRING -> invokeStatic(VALUES_OWNER, "toText", "(Ljava/lang/Object;)Ljava/lang/String;");
            default -> {
                // Object and unknown targets need no conversion.
            }
        }
    }

    /** JVM internal name of the value helper class. */
    static final String VALUES_OWNER = "io/github/dsh/fastscript/core/Values";
}
