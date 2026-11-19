package example.authors

import example.model.Author
import example.model.Book
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

@Singleton
@GraphQLTypeResolver(Book)
class BookAuthors {

    Author author(Book book) {
        return new Author(id: '1', name: 'Frank')
    }

}
