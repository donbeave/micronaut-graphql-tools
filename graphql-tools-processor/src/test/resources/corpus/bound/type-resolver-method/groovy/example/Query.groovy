package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

}
