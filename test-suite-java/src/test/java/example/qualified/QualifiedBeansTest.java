package example.qualified;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.apollo.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.AllBooksQuery;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest(environments = QualifiedBeansFactory.ENVIRONMENT)
class QualifiedBeansTest extends AbstractTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    GraphQLSchema schema;

    @Inject
    GraphQL graphQL;

    @Inject
    @Named("admin")
    GraphQL adminGraphQL;

    @Test
    void qualifiedBeansDoNotReplaceTheLibraryBeans() {
        assertEquals(2, applicationContext.getBeansOfType(GraphQL.class).size());
        assertEquals(2, applicationContext.getBeansOfType(GraphQLSchema.class).size());
        assertNotSame(adminGraphQL, graphQL);
        assertSame(schema, graphQL.getGraphQLSchema());
        assertNotNull(schema.getQueryType().getFieldDefinition("allBooks"));
    }

    @Test
    void requestsRunTheLibrarySchema() {
        ApolloResponse<AllBooksQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new AllBooksQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertEquals(5, response.data.allBooks.size());
    }

}
