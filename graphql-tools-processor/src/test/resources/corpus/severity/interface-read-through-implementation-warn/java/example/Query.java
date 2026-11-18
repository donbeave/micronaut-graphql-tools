package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public PlainNote note() {
        return new PlainNoteImpl();
    }

}
