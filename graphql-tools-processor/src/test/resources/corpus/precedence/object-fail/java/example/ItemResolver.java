package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Item.class)
public class ItemResolver {

    public String label(Item item) {
        return "resolved " + item.getName();
    }

}
