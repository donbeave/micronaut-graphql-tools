package example

import jakarta.inject.Singleton
import groovy.transform.PackageScope
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    @PackageScope
    String internal() {
        null
    }

    Book book(String id) {
        new Book(id)
    }

    List<Book> books(Integer limit) {
        []
    }

}
