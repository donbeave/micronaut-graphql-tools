package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

}
