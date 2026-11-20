package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Book)
class BookResolver {

    Optional<String> author(Book book) {
        Optional.empty()
    }

}
