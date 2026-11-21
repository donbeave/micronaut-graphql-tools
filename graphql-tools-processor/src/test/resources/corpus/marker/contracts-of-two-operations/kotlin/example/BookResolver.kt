package example

import jakarta.inject.Singleton

@Singleton
open class BookResolver : BookQueryContract, BookMutationContract {

    override fun book(id: String): Book = Book()

    override fun book(id: String, title: String): Book = Book()

}
