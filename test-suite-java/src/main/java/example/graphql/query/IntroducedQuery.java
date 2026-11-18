package example.graphql.query;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Introduced
@GraphQLRootResolver
public interface IntroducedQuery {

    String introduced();

}
