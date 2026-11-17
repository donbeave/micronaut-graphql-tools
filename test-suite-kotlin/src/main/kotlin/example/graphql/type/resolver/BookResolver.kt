package example.graphql.type.resolver

import example.ElapsedTime
import example.data.Book
import example.data.BookSeller
import example.graphql.BookSellerDataLoader
import graphql.schema.DataFetchingEnvironment
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import java.util.concurrent.CompletionStage

@GraphQLTypeResolver(Book::class)
open class BookResolver {

    @ElapsedTime
    open fun sellers(book: Book, dfe: DataFetchingEnvironment): CompletionStage<List<BookSeller>> {
        // graphql-java 25 marks the result nullable, 21 and 22 do not: both compile with the explicit type arguments
        val dataLoader = dfe.getDataLoader<String, List<BookSeller>>(BookSellerDataLoader::class.java.name)!!
        return dataLoader.load(book.id)
    }
}
