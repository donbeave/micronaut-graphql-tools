package example

import jakarta.inject.Singleton

@Singleton
class BookSummary implements BookSummaryContract {

    @Override
    String summary(Book book) {
        return 's'
    }

    String helper(Book book) {
        return 'not a field'
    }

}
