package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Named.class)
public class NamedResolver {

    public String name(Named named) {
        return null;
    }

}
