package example.graphql.type.resolver

import example.ElapsedTime
import example.data.Book
import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class BookLabelResolver implements BookLabelResolverContract {

    @ElapsedTime
    @Override
    String label(Book book) {
        book.title + ' (' + book.year + ')'
    }
}
