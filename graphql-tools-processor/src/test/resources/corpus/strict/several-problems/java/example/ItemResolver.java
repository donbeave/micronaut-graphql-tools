package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLTypeResolver(Item.class)
public class ItemResolver {

    public String legacyName(Item item) {
        return item.getName();
    }

}
