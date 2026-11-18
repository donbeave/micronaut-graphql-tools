package example;

import graphql.schema.DataFetchingEnvironment;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLTypeResolver(User.class)
public class UserResolver {

    public String name(User user, String suffix, String prefix, DataFetchingEnvironment environment) {
        return user.getId() + ":" + prefix + "-" + suffix;
    }

}
