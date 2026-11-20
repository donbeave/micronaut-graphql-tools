package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Magazine.class)
public class MagazineResolver {

    public String issue(Magazine magazine) {
        return null;
    }

}
