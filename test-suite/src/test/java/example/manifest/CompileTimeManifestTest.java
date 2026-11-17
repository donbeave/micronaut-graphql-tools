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

    @Inject
    BindingManifest bindingManifest;

    @Test
    void theCompileTimeManifestIsTheStartupManifestWithoutWhatTheProcessorLeftOut() throws IOException {
        List<String> compiled = read("binding-manifest.txt");
        List<String> omitted = read("binding-manifest-omitted.txt").stream().filter(it -> !it.startsWith("#")).toList();

        assertEquals(bindingManifest.getHeader(), compiled.subList(0, 4));
        assertEquals(bindingManifest.getLines().stream().filter(line -> !isOmitted(line, omitted)).toList(),
                compiled.subList(4, compiled.size()));
        assertTrue(omitted.contains("type SecurityError"), String.valueOf(omitted));
    }

    private List<String> read(String file) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(DIRECTORY + file)) {
            assertNotNull(inputStream, "the compile-time checks wrote no " + file);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static boolean isOmitted(String line, List<String> omitted) {
        for (String entry : omitted) {
            String kind = entry.substring(0, entry.indexOf(' '));
            String value = entry.substring(entry.indexOf(' ') + 1);
            boolean match = switch (kind) {
                case "kind" -> line.startsWith("!" + value + " ") || line.contains(" → " + value + " · ");
                case "type" -> line.startsWith(value + " → ") || line.startsWith(value + ".")
                        || line.startsWith(value + "/") || line.startsWith("!ambiguous " + value + ".");
                case "field" -> line.startsWith(value + "(") || line.startsWith(value + ":")
                        || line.startsWith("!ambiguous " + value + " ");
                case "unbound" -> line.equals("!unbound " + value);
                default -> throw new IllegalStateException("unknown entry in the omitted record: " + entry);
            };
            if (match) {
                return true;
            }
        }
        return false;
    }

}
