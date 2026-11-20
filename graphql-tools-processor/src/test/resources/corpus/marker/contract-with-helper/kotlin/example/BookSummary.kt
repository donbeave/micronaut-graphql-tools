package example

import jakarta.inject.Singleton

@Singleton
open class BookSummary : BookSummaryContract {

    override fun summary(book: Book): String = "s"

    open fun helper(book: Book): String = "not a field"

}
