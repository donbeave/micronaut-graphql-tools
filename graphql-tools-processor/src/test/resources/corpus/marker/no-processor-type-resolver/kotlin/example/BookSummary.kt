package example

import jakarta.inject.Singleton

@Singleton
open class BookSummary : BookSummaryContract {

    override fun summary(book: Book): String = "s"

}
