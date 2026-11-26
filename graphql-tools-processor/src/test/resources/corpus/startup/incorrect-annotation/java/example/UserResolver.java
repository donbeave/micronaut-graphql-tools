package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLTypeResolver
public class UserResolver {

    public String gravatar(User user) {
        return null;
    }

}
