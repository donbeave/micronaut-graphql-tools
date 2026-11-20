package example.authors

import jakarta.inject.Singleton
import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class AuthorQuery {

    List<Author> authors(Genre genre) {
        return [new Author(id: '1', name: 'Frank')]
    }

    String stray() {
        return 'x'
    }

}
