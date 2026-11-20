package example;

import jakarta.inject.Singleton;

@Singleton
public class BookSummary implements BookSummaryContract {

    @Override
    public String summary(Book book) {
        return "s";
    }

    public String helper(Book book) {
        return "not a field";
    }

}
