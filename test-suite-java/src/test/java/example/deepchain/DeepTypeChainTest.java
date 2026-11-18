package example.deepchain;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): a chain of 1,000 object types and 1,000 input types binds on the
// default thread stack.
@MicronautTest(environments = DeepTypeChainFactory.ENVIRONMENT)
class DeepTypeChainTest {

    @Inject
    GraphQL graphQL;

    @Test
    void aChainOfAThousandTypesIsBound() {
        ExecutionResult object = graphQL.execute("{ chainRoot { name next { name } } }");
        assertTrue(object.getErrors().isEmpty(), object.getErrors().toString());
        assertEquals(Map.of("chainRoot", Map.of("name", "n", "next", Map.of("name", "n"))), object.getData());

        ExecutionResult input = graphQL.execute("{ chainEcho(input: {name: \"a\", next: {name: \"b\"}}) }");
        assertTrue(input.getErrors().isEmpty(), input.getErrors().toString());
        assertEquals(Map.of("chainEcho", "a>b"), input.getData());
    }

}
