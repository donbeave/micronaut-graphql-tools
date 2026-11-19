package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Item.class)
public class ItemResolver {

    public String label(Item item) {
        return "resolved " + item.getName();
    }

}
