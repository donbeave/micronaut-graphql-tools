package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public Item item() {
        return new Item();
    }

    public String paint(Color color) {
        return color.name();
    }

    public String helper(String text) {
        return text;
    }

}
