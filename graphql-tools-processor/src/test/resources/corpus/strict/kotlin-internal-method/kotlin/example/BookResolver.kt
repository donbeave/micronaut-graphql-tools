package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Book::class)
class BookResolver {

    fun author(book: Book): String? = null

}
