package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    Book book(String id) {
        return new Book(title: 't', author: new Author(name: 'a'))
    }

    List<Book> books() {
        return [new Book(title: 't', author: new Author(name: 'a'))]
    }

}
