package example;

@io.micronaut.context.annotation.Context
@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class ScopedContext {

    public String fieldContext(String extra) {
        return null;
    }

}
