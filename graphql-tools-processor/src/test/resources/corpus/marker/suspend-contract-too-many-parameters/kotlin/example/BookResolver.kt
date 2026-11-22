package example

import jakarta.inject.Singleton

@Singleton
open class BookResolver : BookQueryContract {

    override suspend fun book(id: String, extra: String): Book = Book()

}
