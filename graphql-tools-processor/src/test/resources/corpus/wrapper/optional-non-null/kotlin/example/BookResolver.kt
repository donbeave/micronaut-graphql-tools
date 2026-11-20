package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import java.util.Optional

@Singleton
@GraphQLTypeResolver(Book::class)
class BookResolver {

    fun author(book: Book): Optional<String> = Optional.empty()

}
