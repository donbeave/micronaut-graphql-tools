package example.graphql.type.resolver

import example.data.Book
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

/**
 * A resolver contract, as a code generator would emit it. The annotations are inherited by the implementing bean,
 * which needs no GraphQL annotation of its own (see [BookLabelResolver]). [BookResolver] shows the direct form.
 */
@GraphQLTypeResolver(Book::class)
interface BookLabelResolverContract {

    @GraphQLSchemaCoordinate("Book.label")
    fun label(book: Book): String
}
