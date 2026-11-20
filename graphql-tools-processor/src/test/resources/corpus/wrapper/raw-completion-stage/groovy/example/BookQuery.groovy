package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

import java.util.concurrent.CompletionStage

@Singleton
@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return new Book(id)
    }

    CompletionStage books(Integer limit) {
        return null
    }

}
