package example.host;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.apollo.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.AllBooksQuery;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The application supplies the {@code TypeDefinitionRegistry}, consumes only the library's {@link GraphQLSchema} and
 * builds its own {@link GraphQL} ({@link HostGraphQLFactory}); HTTP requests run on that engine.
 */
@MicronautTest(environments = HostGraphQLFactory.ENVIRONMENT)
class HostBuildsGraphQLTest extends AbstractTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    GraphQLSchema schema;

    @Inject
    GraphQL graphQL;

    @Inject
    OperationCounter operationCounter;

    @Test
    void theApplicationGraphQLIsTheOnlyOneAndRunsTheLibrarySchema() {
        assertEquals(1, applicationContext.getBeansOfType(GraphQL.class).size());
        assertEquals(1, applicationContext.getBeansOfType(GraphQLSchema.class).size());
        assertSame(schema, graphQL.getGraphQLSchema());
        assertNotNull(schema.getQueryType().getFieldDefinition("allBooks"));
    }

    @Test
    void requestsRunOnTheApplicationGraphQL() {
        int before = operationCounter.getOperations();

        ApolloResponse<AllBooksQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new AllBooksQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertEquals(5, response.data.allBooks.size());
        assertEquals("The Fractured Sage", response.data.allBooks.getFirst().title);
        assertEquals(2, response.data.allBooks.getFirst().sellers.size());
        assertEquals(before + 1, operationCounter.getOperations());
    }

}
