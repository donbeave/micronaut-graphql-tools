package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Named.class)
public class NamedResolver {

    public String name(Named named) {
        return null;
    }

}
