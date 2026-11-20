package example

import io.micronaut.graphql.tools.resolver.MutationResolver
import jakarta.inject.Singleton

@Singleton
class BookMutation implements MutationResolver {

    String ping() {
        return 'pong'
    }

    String book(String id) {
        return id
    }

}
