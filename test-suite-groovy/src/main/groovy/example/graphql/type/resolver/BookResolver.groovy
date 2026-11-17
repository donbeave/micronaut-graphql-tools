package example.graphql.type.resolver

import example.ElapsedTime
import example.data.Book
import example.data.BookSeller
import example.graphql.BookSellerDataLoader
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.dataloader.DataLoader

import java.util.concurrent.CompletionStage

@CompileStatic
@GraphQLTypeResolver(Book)
class BookResolver {

    @ElapsedTime
    CompletionStage<List<BookSeller>> sellers(Book book, DataFetchingEnvironment dfe) {
        DataLoader<String, List<BookSeller>> dataLoader = dfe.getDataLoader(BookSellerDataLoader.name)
        return dataLoader.load(book.id)
    }
}
