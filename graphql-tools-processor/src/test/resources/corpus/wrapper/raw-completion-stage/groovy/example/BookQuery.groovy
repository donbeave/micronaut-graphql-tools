package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

import java.util.concurrent.CompletionStage

@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return new Book(id)
    }

    CompletionStage books(Integer limit) {
        return null
    }

}
