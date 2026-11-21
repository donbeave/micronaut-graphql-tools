package example

import jakarta.inject.Singleton

@Singleton
open class BookQuery : BookQueryContract {

    override fun book(id: String): Book = Book()

    open fun book(id: String, limit: Int): Book = Book()

}
