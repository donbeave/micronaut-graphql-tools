package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Book)
class BookResolver {

    Optional<String> author(Book book) {
        Optional.empty()
    }

}
