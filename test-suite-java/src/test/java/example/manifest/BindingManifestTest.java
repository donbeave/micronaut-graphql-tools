package example.manifest;

import io.micronaut.graphql.tools.BindingManifest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class BindingManifestTest {

    @Inject
    BindingManifest bindingManifest;

    @Test
    void theManifestOfTheSampleEqualsTheGoldenFile() throws IOException {
        try (InputStream golden = getClass().getResourceAsStream("/binding-manifest.txt")) {
            assertEquals(new String(golden.readAllBytes(), StandardCharsets.UTF_8),
                    String.join("\n", bindingManifest.getLines()) + "\n");
        }
    }

    @Test
    void aResolverOverridingObjectMethodsHasNoUnboundFinding() {
        assertTrue(bindingManifest.getLines().stream().noneMatch(it -> it.startsWith("!unbound ")
                && (it.contains("toString") || it.contains("equals") || it.contains("hashCode"))),
                String.valueOf(bindingManifest.getLines()));
    }

}
