package example.graphql.type.resolver;

import example.ElapsedTime;
import example.data.Book;
import example.data.BookSeller;
import example.data.BookSellerRepository;
import example.graphql.BookSellerDataLoader;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import org.dataloader.DataLoader;

import java.util.List;
import java.util.concurrent.CompletionStage;

@GraphQLTypeResolver(Book.class)
public class BookResolver {

    private final BookSellerRepository bookSellerRepository;

    public BookResolver(BookSellerRepository bookSellerRepository) {
        this.bookSellerRepository = bookSellerRepository;
    }

    @ElapsedTime
    public CompletionStage<List<BookSeller>> sellers(Book book, DataFetchingEnvironment dfe) {
        DataLoader<String, List<BookSeller>> dataLoader = dfe.getDataLoader(BookSellerDataLoader.class.getName());

        return dataLoader.load(book.getId());
    }

}
