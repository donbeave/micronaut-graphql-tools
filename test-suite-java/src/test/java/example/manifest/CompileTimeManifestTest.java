package example.manifest;

import io.micronaut.graphql.tools.BindingManifest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class CompileTimeManifestTest {

    private static final String DIRECTORY = "/META-INF/micronaut-graphql-tools/";

    private static final String GRAPHQL_JAVA_LINE = "# graphql-java: ";

    @Inject
    BindingManifest bindingManifest;

    @Test
    void theCompileTimeManifestIsTheStartupManifestWithoutWhatTheProcessorLeftOut() throws IOException {
        List<String> compiled = read("binding-manifest.txt");
        List<String> omitted = read("binding-manifest-omitted.txt").stream().filter(it -> !it.startsWith("#")).toList();

        // The processor records the graphql-java it compiled against. The binary compatibility build runs the tests on
        // another graphql-java than the one main code and processor were compiled on, so that line is not compared.
        assertEquals(withoutGraphqlJavaLine(bindingManifest.getHeader()), withoutGraphqlJavaLine(compiled.subList(0, 4)));
        assertTrue(compiled.subList(0, 4).stream().anyMatch(it -> it.startsWith(GRAPHQL_JAVA_LINE)), String.valueOf(compiled));
        assertEquals(bindingManifest.getLines().stream().filter(line -> !isOmitted(line, omitted)).toList(),
                compiled.subList(4, compiled.size()));
        assertTrue(omitted.contains("type SecurityError"), String.valueOf(omitted));
    }

    private static List<String> withoutGraphqlJavaLine(List<String> header) {
        return header.stream().filter(it -> !it.startsWith(GRAPHQL_JAVA_LINE)).toList();
    }

    private List<String> read(String file) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(DIRECTORY + file)) {
            assertNotNull(inputStream, "the compile-time checks wrote no " + file);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    // the omitted record names the kinds that the JSR-269 processor writes; the startup writes the codes (#237)
    private static List<String> startupKinds(String kind) {
        return switch (kind) {
            case "unbound" -> List.of("unbound-resolver-method", "unbound-enum-constant", "unmapped-input-property");
            case "unusedDictionary" -> List.of("unused-dictionary-entry");
            case "unwiredDirective" -> List.of("unwired-directive");
            case "ambiguous" -> List.of("precedence-ambiguity");
            case "unreachable" -> List.of("unreachable-type", "unreachable-value-type");
            default -> List.of(kind);
        };
    }

    private static boolean isOmitted(String line, List<String> omitted) {
        for (String entry : omitted) {
            String kind = entry.substring(0, entry.indexOf(' '));
            String value = entry.substring(entry.indexOf(' ') + 1);
            boolean match = switch (kind) {
                case "kind" -> startupKinds(value).stream().anyMatch(it -> line.startsWith("!" + it + " "))
                        || line.contains(" → " + value + " · ");
                case "type" -> line.startsWith(value + " → ") || line.startsWith(value + ".")
                        || line.startsWith(value + "/") || line.startsWith("!precedence-ambiguity " + value + ".");
                case "field" -> line.startsWith(value + "(") || line.startsWith(value + ":")
                        || line.startsWith("!precedence-ambiguity " + value + " ");
                case "unbound" -> line.equals("!unbound-resolver-method " + value)
                        || line.equals("!unbound-enum-constant " + value)
                        || line.equals("!unmapped-input-property " + value);
                default -> throw new IllegalStateException("unknown entry in the omitted record: " + entry);
            };
            if (match) {
                return true;
            }
        }
        return false;
    }

}
