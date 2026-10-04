import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Minimal, dependency-free Maven resolver for this project's build.
 *
 * <p>It exists because the build must be reproducible without Gradle or Maven on the
 * machine: it resolves {@code -SNAPSHOT} metadata, {@code dependencyManagement} BOM
 * imports and transitive compile-scope dependencies, then caches the jars under
 * {@code libs/}. Every artifact is fetched over plain HTTPS with the JDK HTTP client.</p>
 *
 * <p>Usage: {@code java Fetch <libsDir> <repoUrl>[,<repoUrl>...] <group:artifact:version>...}</p>
 */
public final class Fetch {

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Artifacts that are only referenced from Paper's deprecated code paths or from its
     * internal Maven tooling. They drag in legacy Guava/Gson variants that would shadow the
     * modern ones on the compile classpath, so the build deliberately ignores them; no code
     * in this project touches their types.
     */
    private static final Set<String> SKIPPED = Set.of(
            "maven-resolver-provider",
            "maven-model",
            "maven-model-builder",
            "maven-artifact",
            "maven-repository-metadata",
            "maven-resolver-api",
            "maven-resolver-spi",
            "maven-resolver-util",
            "maven-resolver-impl",
            "maven-resolver-connector-basic",
            "maven-resolver-transport-http",
            "plexus-utils",
            "guava:21.0",
            "gson:2.8.8",
            "gson:2.8.0",
            "slf4j-api:1.7.36");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final List<String> repositories;
    private final Path libs;
    private final Map<String, String> properties = new HashMap<>();
    private final Map<String, String> managed = new LinkedHashMap<>();
    private final Map<String, String> metadataCache = new HashMap<>();
    private final Set<String> resolved = new LinkedHashSet<>();
    private final List<String> failures = new ArrayList<>();

    private Fetch(List<String> repositories, Path libs) {
        this.repositories = repositories;
        this.libs = libs;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: Fetch <libsDir> <repoUrl[,repoUrl...]> <g:a:v>...");
            System.exit(2);
        }
        List<String> repos = new ArrayList<>();
        for (String repo : args[1].split(",")) {
            repos.add(repo.endsWith("/") ? repo : repo + "/");
        }
        Fetch fetcher = new Fetch(repos, Path.of(args[0]));
        Files.createDirectories(fetcher.libs);
        for (int i = 2; i < args.length; i++) {
            String[] parts = args[i].split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("bad coordinate: " + args[i]);
            }
            fetcher.resolveArtifact(parts[0], parts[1], parts[2], null, new ArrayDeque<>());
        }
        for (String jar : fetcher.resolved) {
            System.out.println("RESOLVED " + jar);
        }
        if (!fetcher.failures.isEmpty()) {
            for (String failure : fetcher.failures) {
                System.err.println("FETCH-FAILURE " + failure);
            }
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ resolution

    private void resolveArtifact(String group, String artifact, String version, String classifier, Deque<String> path) {
        String key = group + ":" + artifact + ":" + version + (classifier == null ? "" : ":" + classifier);
        if (!resolved.add(key)) {
            return;
        }
        if (path.contains(key) || path.size() > 24) {
            resolved.remove(key);
            return;
        }
        path.push(key);
        try {
            String actualVersion = resolveSnapshotVersion(group, artifact, version);
            if (actualVersion.contains("${")) {
                // Unresolved Maven model placeholder: skip instead of building a broken URL.
                resolved.remove(key);
                return;
            }
            String base = group.replace('.', '/') + "/" + artifact + "/" + version + "/"
                    + artifact + "-" + actualVersion;
            String jarName = artifact + "-" + version + (classifier == null ? "" : "-" + classifier) + ".jar";
            if (fetch(base + (classifier == null ? "" : "-" + classifier) + ".jar", jarName) == null) {
                resolved.remove(key);
                return;
            }
            if (classifier == null) {
                String pomText = fetchText(base + ".pom");
                if (pomText != null) {
                    walkPom(group, artifact, version, pomText, path);
                }
            }
        } finally {
            path.pop();
        }
    }

    private void walkPom(String group, String artifact, String version, String pomText, Deque<String> path) {
        Document document = parse(pomText, group + ":" + artifact + ":" + version);
        if (document == null) {
            return;
        }
        Element project = document.getDocumentElement();

        // Properties feed ${...} placeholders such as ${adventure.version}.
        for (Element element : children(project, "properties")) {
            for (Element property : children(element, null)) {
                properties.put(property.getTagName(), property.getTextContent().trim());
            }
        }
        for (Element parent : children(project, "parent")) {
            String parentGroup = text(parent, "groupId");
            String parentArtifact = text(parent, "artifactId");
            String parentVersion = text(parent, "version");
            if (parentGroup != null && parentArtifact != null && parentVersion != null) {
                String actualParent = resolveSnapshotVersion(parentGroup, parentArtifact, parentVersion);
                String parentPom = fetchText(parentGroup.replace('.', '/') + "/" + parentArtifact + "/" + parentVersion
                        + "/" + parentArtifact + "-" + actualParent + ".pom");
                if (parentPom != null) {
                    walkPom(parentGroup, parentArtifact, parentVersion, parentPom, path);
                }
            }
        }

        // dependencyManagement: BOM imports contribute the versions of unversioned deps.
        for (Element management : children(project, "dependencyManagement")) {
            for (Element deps : children(management, "dependencies")) {
                for (Element dependency : children(deps, "dependency")) {
                    String depGroup = expand(text(dependency, "groupId"));
                    String depArtifact = expand(text(dependency, "artifactId"));
                    String depVersion = expand(text(dependency, "version"));
                    String type = expand(text(dependency, "type"));
                    String scope = expand(text(dependency, "scope"));
                    if (depGroup == null || depArtifact == null) {
                        continue;
                    }
                    if ("pom".equals(type) && "import".equals(scope)) {
                        importBom(depGroup, depArtifact, depVersion);
                    } else if (depVersion != null) {
                        managed.putIfAbsent(depGroup + ":" + depArtifact, depVersion);
                    }
                }
            }
        }

        for (Element dependencies : children(project, "dependencies")) {
            for (Element dependency : children(dependencies, "dependency")) {
                String depGroup = expand(text(dependency, "groupId"));
                String depArtifact = expand(text(dependency, "artifactId"));
                String depVersion = expand(text(dependency, "version"));
                String scope = expand(text(dependency, "scope"));
                String type = expand(text(dependency, "type"));
                String classifier = expand(text(dependency, "classifier"));
                boolean optional = "true".equals(expand(text(dependency, "optional")));
                Set<String> exclusions = new LinkedHashSet<>();
                for (Element exclusionsElement : children(dependency, "exclusions")) {
                    for (Element exclusion : children(exclusionsElement, "exclusion")) {
                        exclusions.add(expand(text(exclusion, "groupId")) + ":" + expand(text(exclusion, "artifactId")));
                    }
                }
                if (depGroup == null || depArtifact == null || "pom".equals(type) || optional) {
                    continue;
                }
                if (SKIPPED.contains(depArtifact) || SKIPPED.contains(depGroup + ":" + depArtifact)) {
                    continue;
                }
                if (scope != null && !scope.equals("compile") && !scope.equals("runtime")) {
                    continue;
                }
                if (exclusions.contains("*:*") || exclusions.contains(depGroup + ":*")
                        || exclusions.contains(depGroup + ":" + depArtifact)) {
                    continue;
                }
                if (depVersion == null) {
                    depVersion = managed.get(depGroup + ":" + depArtifact);
                }
                if (depVersion == null) {
                    continue;
                }
                resolveArtifact(depGroup, depArtifact, depVersion, classifier, path);
            }
        }
    }

    private void importBom(String group, String artifact, String version) {
        if (version == null) {
            return;
        }
        try {
            String actual = resolveSnapshotVersion(group, artifact, version);
            String pom = fetchText(group.replace('.', '/') + "/" + artifact + "/" + version + "/"
                    + artifact + "-" + actual + ".pom");
            if (pom == null) {
                return;
            }
            Document document = parse(pom, group + ":" + artifact + ":" + version);
            if (document == null) {
                return;
            }
            Element root = document.getDocumentElement();
            for (Element element : children(root, "properties")) {
                for (Element property : children(element, null)) {
                    properties.put(property.getTagName(), property.getTextContent().trim());
                }
            }
            for (Element management : children(root, "dependencyManagement")) {
                for (Element deps : children(management, "dependencies")) {
                    for (Element dependency : children(deps, "dependency")) {
                        String depGroup = expand(text(dependency, "groupId"));
                        String depArtifact = expand(text(dependency, "artifactId"));
                        String depVersion = expand(text(dependency, "version"));
                        if (depGroup != null && depArtifact != null && depVersion != null) {
                            managed.putIfAbsent(depGroup + ":" + depArtifact, depVersion);
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A missing BOM only means unversioned dependencies stay unresolved.
        }
    }

    private String expand(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PROPERTY.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String replacement = properties.get(matcher.group(1));
            matcher.appendReplacement(builder,
                    Matcher.quoteReplacement(replacement == null ? matcher.group(0) : replacement));
        }
        matcher.appendTail(builder);
        String result = builder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Maps {@code 1.2.3-SNAPSHOT} to the timestamped build published in the repository,
     * because snapshot files are not stored under the literal {@code -SNAPSHOT} name.
     */
    private String resolveSnapshotVersion(String group, String artifact, String version) {
        if (!version.endsWith("-SNAPSHOT")) {
            return version;
        }
        String base = group.replace('.', '/') + "/" + artifact + "/" + version + "/";
        String metadata = metadataCache.computeIfAbsent(base, key -> {
            String text = fetchText(key + "maven-metadata.xml");
            return text == null ? "" : text;
        });
        if (metadata.isEmpty()) {
            return version;
        }
        Document document = parse(metadata, group + ":" + artifact + ":" + version);
        if (document == null) {
            return version;
        }
        String jarValue = null;
        for (Element versioning : children(document.getDocumentElement(), "versioning")) {
            for (Element candidate : children(versioning, "snapshotVersions")) {
                for (Element entry : children(candidate, "snapshotVersion")) {
                    String extension = text(entry, "extension");
                    String classifier = text(entry, "classifier");
                    String value = text(entry, "value");
                    if (value == null || classifier != null) {
                        continue;
                    }
                    if ("pom".equals(extension)) {
                        return value;
                    }
                    if ("jar".equals(extension)) {
                        jarValue = value;
                    }
                }
            }
        }
        return jarValue != null ? jarValue : version;
    }

    // ------------------------------------------------------------------ transport

    private Path fetch(String relative, String fileName) {
        Path target = libs.resolve(fileName);
        if (Files.isRegularFile(target) && size(target) > 0) {
            return target;
        }
        byte[] bytes = get(relative);
        if (bytes == null) {
            failures.add(relative);
            return null;
        }
        try {
            Files.write(target, bytes);
        } catch (IOException error) {
            throw new IllegalStateException("cannot write " + target, error);
        }
        System.out.println("DOWNLOADED " + fileName + " (" + bytes.length + " bytes)");
        return target;
    }

    private String fetchText(String relative) {
        byte[] bytes = get(relative);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] get(String relative) {
        IOException last = null;
        for (String repository : repositories) {
            String url = repository + relative;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "dsh-build-fetch")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                if (response.statusCode() != 404) {
                    System.out.println("HTTP " + response.statusCode() + " " + url);
                }
            } catch (IOException error) {
                last = error;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
        if (last != null) {
            System.out.println("NETWORK " + last.getMessage() + " for " + relative);
        }
        return null;
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException error) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ xml helpers

    private static Document parse(String xml, String what) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            System.out.println("XML-PARSE-FAILURE " + what + ": " + error.getMessage());
            return null;
        }
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (name == null || node.getNodeName().equals(name)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String text(Element parent, String name) {
        for (Element child : children(parent, name)) {
            String value = child.getTextContent();
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
