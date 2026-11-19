package example.authors

import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class AuthorQuery {

    List<Author> authors(Genre kind) {
        return [new Author(id: '1', name: 'Frank')]
    }

}
