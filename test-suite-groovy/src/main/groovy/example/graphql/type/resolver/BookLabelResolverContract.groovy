package example.graphql.type.resolver

import example.data.Book
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Book)
interface BookLabelResolverContract {

    @GraphQLSchemaCoordinate('Book.label')
    String label(Book book)
}
