package example.authors

import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class AuthorQuery {

    List<Author> authors(Genre genre) {
        return [new Author(id: '1', name: 'Frank')]
    }

    String stray() {
        return 'x'
    }

}
