package example

import jakarta.inject.Singleton

@Singleton
class BookSummary implements BookSummaryContract {

    @Override
    String summary(Book book) {
        return 's'
    }

}
