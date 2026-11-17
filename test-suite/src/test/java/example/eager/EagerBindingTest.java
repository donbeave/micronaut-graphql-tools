package example.eager;

import example.host.HostGraphQLFactory;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema is bound when the application starts: a field no resolver binds fails the server start, whether the
 * library or the application builds the {@code GraphQL} bean.
 */
class EagerBindingTest {

    private static final Map<String, Object> PORT = Map.of("micronaut.server.port", -1);

    @Test
    void aValidSchemaStartsTheServerWithTheSchemaAlreadyBound() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, PORT)) {
            assertNotNull(server.getApplicationContext().getBean(GraphQLSchema.class).getQueryType()
                    .getFieldDefinition("allBooks"));
        }
    }

    @Test
    void aBrokenBindingFailsTheServerStart() {
        BeanInstantiationException e = assertThrows(BeanInstantiationException.class,
                () -> ApplicationContext.run(EmbeddedServer.class, PORT, BrokenBindingFactory.ENVIRONMENT));

        assertTrue(rootCause(e).getMessage().contains("Query.unbound"), rootCause(e).getMessage());
    }

    @Test
    void aBrokenBindingFailsTheServerStartWhenTheApplicationBuildsItsOwnGraphQL() {
        BeanInstantiationException e = assertThrows(BeanInstantiationException.class,
                () -> ApplicationContext.run(EmbeddedServer.class, PORT,
                        BrokenBindingFactory.ENVIRONMENT, HostGraphQLFactory.ENVIRONMENT));

        assertTrue(rootCause(e).getMessage().contains("Query.unbound"), rootCause(e).getMessage());
    }

    @Test
    void withEagerInitOffTheBrokenServerStartsAndTheBindingFailsOnFirstUse() {
        Map<String, Object> lazy = Map.of("micronaut.server.port", -1, "micronaut.graphql.tools.eager-init", false);
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, lazy,
                BrokenBindingFactory.ENVIRONMENT)) {
            assertThrows(BeanInstantiationException.class,
                    () -> server.getApplicationContext().getBean(GraphQLSchema.class));
        }
    }

    private static Throwable rootCause(Throwable e) {
        while (e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        return e;
    }

}
