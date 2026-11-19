package example;

@io.micronaut.context.annotation.Prototype
@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class ScopedPrototype {

    public String fieldPrototype(String extra) {
        return null;
    }

}
