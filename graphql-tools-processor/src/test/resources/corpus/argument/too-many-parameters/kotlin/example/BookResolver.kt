package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import java.util.Optional

@GraphQLTypeResolver(Book::class)
class BookResolver {

    fun author(book: Book): Optional<String> = Optional.empty()

}
