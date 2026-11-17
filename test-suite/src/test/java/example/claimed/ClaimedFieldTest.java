package example.claimed;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.graphql.tools.BindingManifest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field that a {@code RuntimeWiringCustomizer} claims has no Java member, and is served by the customizer's data
 * fetcher: on the JVM and in a native image.
 */
@MicronautTest(environments = ClaimedFieldCustomizers.ENVIRONMENT)
class ClaimedFieldTest {

    @Inject
    GraphQL graphQL;

    @Inject
    BindingManifest manifest;

    @Test
    void aClaimedFieldIsServedByTheCustomizer() {
        ExecutionResult result = graphQL.execute("{ serverVersion allBooks { title } }");

        assertTrue(result.getErrors().isEmpty());
        Map<String, Object> data = result.getData();
        assertEquals("1.0", data.get("serverVersion"));
        assertEquals(5, ((java.util.List<?>) data.get("allBooks")).size());
    }

    @Test
    void aClaimedFieldIsListedInTheManifest() {
        assertTrue(manifest.getLines().stream().anyMatch(it -> it.startsWith("Query.serverVersion: String → claimed · ")));
    }

}
