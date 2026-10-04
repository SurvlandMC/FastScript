import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

/**
 * Packages the plugin jar and relocates the bundled ASM copy into a private namespace.
 *
 * <p>Shading a bytecode library is what keeps the engine independent from whatever ASM
 * version the server or another plugin happens to provide: generated classes reference the
 * relocated names, so a server-side ASM upgrade can never break script compilation.
 * Relocation happens at the bytecode level rather than through a build-tool plugin,
 * because this project builds with plain {@code javac}.</p>
 *
 * <p>Usage: {@code Package <outJar> <relocateFrom> <relocateTo> <inputDir>...}</p>
 * Input directories may be class directories (produced by javac) or jar files.
 */
public final class Package {

    private final String fromPrefix;
    private final String toPrefix;
    private final SimpleRemapper remapper;
    private final List<Path> inputs;

    private Package(String fromPrefix, String toPrefix, Map<String, String> classMappings,
            List<Path> inputs) {
        this.fromPrefix = fromPrefix;
        this.toPrefix = toPrefix;
        this.inputs = inputs;
        // ASM's SimpleRemapper replaces whole internal names rather than prefixes, so every
        // shaded class needs an exact entry in the map.
        this.remapper = new SimpleRemapper(new LinkedHashMap<>(classMappings));
    }

    /** Maps every class in the given jars from {@code fromPrefix} to {@code toPrefix}. */
    private static Map<String, String> mappingsFor(List<Path> jars, String fromPrefix,
            String toPrefix) throws IOException {
        String from = fromPrefix.replace('.', '/');
        String to = toPrefix.replace('.', '/');
        Map<String, String> mappings = new LinkedHashMap<>();
        for (Path jarPath : jars) {
            if (!Files.isRegularFile(jarPath) || !jarPath.toString().endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                for (JarEntry entry : jar.stream().toList()) {
                    String name = entry.getName();
                    if (!name.endsWith(".class") || name.equals("module-info.class")) {
                        continue;
                    }
                    String internal = name.substring(0, name.length() - ".class".length());
                    if (internal.startsWith(from + "/") || internal.equals(from)) {
                        mappings.put(internal, to + internal.substring(from.length()));
                    }
                }
            }
        }
        return mappings;
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "--mappings".equals(args[0])) {
            // Emits the exact rename table so another tool run can reuse it.
            Map<String, String> mappings = mappingsFor(
                    List.of(Path.of(args[3])), args[1], args[2]);
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                System.out.println(entry.getKey() + "-->" + entry.getValue());
            }
            return;
        }
        if (args.length > 0 && "--list-classes".equals(args[0])) {
            for (int i = 1; i < args.length; i++) {
                try (JarFile jar = new JarFile(Path.of(args[i]).toFile())) {
                    for (JarEntry entry : jar.stream().toList()) {
                        String name = entry.getName();
                        if (name.endsWith(".class") && !name.equals("module-info.class")) {
                            System.out.println(name.substring(0, name.length() - ".class".length()));
                        }
                    }
                }
            }
            return;
        }
        if (args.length < 4) {
            System.err.println("usage: Package [--relocate-only] <outJar|relocateDir> "
                    + "<relocateFrom> <relocateTo> <input>...");
            System.exit(2);
        }
        boolean relocateOnly = "--relocate-only".equals(args[0]);
        int offset = relocateOnly ? 1 : 0;
        Path out = Path.of(args[offset]);
        List<Path> inputs = new ArrayList<>();
        for (int i = offset + 3; i < args.length; i++) {
            inputs.add(Path.of(args[i]));
        }
        Map<String, String> mappings = mappingsFor(inputs, args[offset + 1], args[offset + 2]);
        Package packager = new Package(args[offset + 1], args[offset + 2], mappings, inputs);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        packager.collect(entries);

        if (relocateOnly) {
            // Writes the shaded sources so javac and the final packaging step can use them.
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                Path target = out.resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
            }
            System.out.println("RELOCATED " + entries.size() + " entries into " + out);
            return;
        }

        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Implementation-Title", "FastScript");
        attributes.putValue("Implementation-Vendor", "FastScript");

        try (OutputStream fileOut = Files.newOutputStream(out);
                JarOutputStream jar = new JarOutputStream(fileOut, manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(0L);
                jar.putNextEntry(jarEntry);
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        long classes = entries.keySet().stream().filter(name -> name.endsWith(".class")).count();
        System.out.println("PACKAGED " + out.getFileName() + " entries=" + entries.size() + " classes=" + classes
                + " bytes=" + Files.size(out));
    }

    private void collect(Map<String, byte[]> entries) throws IOException {
        for (Path input : inputs) {
            if (!Files.exists(input)) {
                throw new IOException("input does not exist: " + input);
            }
            if (Files.isDirectory(input)) {
                collectDirectory(input, entries);
            } else {
                collectJar(input, entries);
            }
        }
    }

    private void collectDirectory(Path root, Map<String, byte[]> entries) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (skip(relative, false)) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                put(entries, relative, bytes);
            }
        }
    }

    private void collectJar(Path jarPath, Map<String, byte[]> entries) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<JarEntry> jarEntries = jar.stream().filter(entry -> !entry.isDirectory()).toList();
            for (JarEntry entry : jarEntries) {
                String name = entry.getName();
                if (skip(name, true)) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    put(entries, name, in.readAllBytes());
                }
            }
        }
    }

    private void put(Map<String, byte[]> entries, String name, byte[] bytes) {
        String target = name;
        byte[] payload = bytes;
        if (name.endsWith(".class")) {
            String internal = name.substring(0, name.length() - ".class".length());
            String relocated = relocateInternalName(internal);
            target = relocated + ".class";
            payload = rewrite(bytes);
        }
        // First definition wins: plugin code already compiled against the relocated ASM
        // package is authoritative, and javac output is listed before the ASM jars.
        entries.putIfAbsent(target, payload);
    }

    private byte[] rewrite(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassRemapper(writer, remapper) {
            @Override
            public void visitSource(String source, String debug) {
                // Drop SourceFile/SourceDebug so the shaded copy carries no original paths.
                super.visitSource(null, null);
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private String relocateInternalName(String internalName) {
        String from = fromPrefix.replace('.', '/');
        if (internalName.equals(from) || internalName.startsWith(from + "/")) {
            return toPrefix.replace('.', '/') + internalName.substring(from.length());
        }
        return internalName;
    }

    private boolean skip(String name, boolean fromJar) {
        if (name.equals("module-info.class") || name.endsWith("/module-info.class")) {
            return true;
        }
        if (name.startsWith("META-INF/")) {
            // Manifests, signatures and Gradle module metadata are not valid once shaded.
            if (name.equals("META-INF/MANIFEST.MF")
                    || name.endsWith(".SF")
                    || name.endsWith(".DSA")
                    || name.endsWith(".RSA")
                    || name.endsWith(".kotlin_module")
                    || name.startsWith("META-INF/maven/")
                    || name.startsWith("META-INF/versions/")) {
                return true;
            }
        }
        return fromJar && name.equals("META-INF/");
    }
}
