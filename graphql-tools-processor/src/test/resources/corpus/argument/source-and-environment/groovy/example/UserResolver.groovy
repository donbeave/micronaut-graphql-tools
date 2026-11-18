package example

import graphql.schema.DataFetchingEnvironment
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

@Singleton
@GraphQLTypeResolver(User)
class UserResolver {

    String name(User user, String suffix, String prefix, DataFetchingEnvironment environment) {
        return user.id + ':' + prefix + '-' + suffix
    }

}
