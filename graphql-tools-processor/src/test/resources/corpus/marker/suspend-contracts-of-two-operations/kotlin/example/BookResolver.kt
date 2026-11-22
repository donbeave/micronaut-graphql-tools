package example

import jakarta.inject.Singleton

@Singleton
open class BookResolver : BookQueryContract, BookMutationContract {

    override suspend fun book(id: String): Book = Book()

    override suspend fun book(id: String, title: String): Book = Book()

}
