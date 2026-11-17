package example.graphql.type.resolver

import example.ElapsedTime
import example.data.Book
import example.data.BookSeller
import example.graphql.BookSellerDataLoader
import graphql.schema.DataFetchingEnvironment
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.dataloader.DataLoader
import java.util.concurrent.CompletionStage

@GraphQLTypeResolver(Book::class)
open class BookResolver {

    @ElapsedTime
    open fun sellers(book: Book, dfe: DataFetchingEnvironment): CompletionStage<List<BookSeller>> {
        val dataLoader: DataLoader<String, List<BookSeller>> = dfe.getDataLoader(BookSellerDataLoader::class.java.name)
        return dataLoader.load(book.id)
    }
}
