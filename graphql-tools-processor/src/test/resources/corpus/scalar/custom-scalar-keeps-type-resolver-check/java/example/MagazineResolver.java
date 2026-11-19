package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Magazine.class)
public class MagazineResolver {

    public String issue(Magazine magazine) {
        return null;
    }

}
