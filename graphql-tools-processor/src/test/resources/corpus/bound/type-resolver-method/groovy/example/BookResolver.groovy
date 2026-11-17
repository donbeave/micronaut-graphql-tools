package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

@Singleton
@GraphQLTypeResolver(Book)
class BookResolver {

    String summary(Book book) {
        return 'Summary of ' + book.title
    }

}
