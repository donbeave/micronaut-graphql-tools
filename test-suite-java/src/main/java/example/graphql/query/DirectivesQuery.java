package example.graphql.query;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class DirectivesQuery {

    public String shout() {
        return "quiet";
    }

    public String tagged() {
        return "value";
    }

}
