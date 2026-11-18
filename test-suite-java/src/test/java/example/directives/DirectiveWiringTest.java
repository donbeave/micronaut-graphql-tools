package example.directives;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.java.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.DirectivesQuery;
import graphql.schema.GraphQLSchema;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class DirectiveWiringTest extends AbstractTest {

    @Inject
    GraphQLSchema schema;

    @Test
    void aNamedSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        ApolloResponse<DirectivesQuery.Data> response = execute();

        assertEquals("QUIET", response.data.shout);
    }

    @Test
    void anEveryElementSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        ApolloResponse<DirectivesQuery.Data> response = execute();

        assertEquals("[t] value", response.data.tagged);
    }

    @Test
    void aWiringDoesNotChangeAFieldThatDoesNotApplyItsDirective() {
        assertEquals("introduced introduced",
                Rx3Apollo.single(apolloClient.query(new example.client.IntroducedQuery())).blockingGet().data.introduced);
    }

    @Test
    void aSchemaLevelDirectiveIsHandledByTheSchemaCustomizerThatItIsListedFor() {
        assertNotNull(schema.getSchemaAppliedDirective("audit"));
        assertEquals("audited", execute().data.__schema.description);
    }

    private ApolloResponse<DirectivesQuery.Data> execute() {
        ApolloResponse<DirectivesQuery.Data> response = Rx3Apollo.single(apolloClient.query(new DirectivesQuery())).blockingGet();
        assertFalse(response.hasErrors(), String.valueOf(response.errors));
        assertNotNull(response.data);
        return response;
    }

}
