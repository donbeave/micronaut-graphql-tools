package example;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import io.micronaut.graphql.tools.annotation.GraphQLContextValue;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;
import java.security.Principal;
import java.util.Locale;

@Singleton
@GraphQLRootResolver
public class Query {

    public String book(Locale locale, DataFetchingEnvironment environment, String id, GraphQLContext context,
                       @GraphQLContextValue("tenant") String tenant, DataFetchingFieldSelectionSet selection,
                       Principal principal) {
        return id;
    }

}
