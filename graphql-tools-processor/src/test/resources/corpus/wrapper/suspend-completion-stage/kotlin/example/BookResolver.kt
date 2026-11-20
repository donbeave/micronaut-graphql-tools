package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Book::class)
class BookResolver {

    suspend fun author(book: Book): String? = "Herbert"

}
