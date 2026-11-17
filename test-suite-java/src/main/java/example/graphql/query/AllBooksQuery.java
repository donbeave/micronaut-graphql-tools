package example.graphql.query;

import example.ElapsedTime;
import example.data.Book;
import example.data.BookRepository;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class AllBooksQuery {

    private final BookRepository bookRepository;

    public AllBooksQuery(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @ElapsedTime
    public List<Book> allBooks() {
        return bookRepository.findAll();
    }

}
