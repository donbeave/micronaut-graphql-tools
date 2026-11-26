package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class Query {

    public Gadget gadget() {
        return new Gadget();
    }

}
