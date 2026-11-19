package example;

@jakarta.inject.Singleton
@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class ScopedSingleton {

    public String fieldSingleton() {
        return null;
    }

}
