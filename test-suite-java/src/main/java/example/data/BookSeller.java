package example.data;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class BookSeller {

    private final String bookId;
    private final String name;
    private final Float price;

    public BookSeller(String bookId, String name, Float price) {
        this.bookId = bookId;
        this.name = name;
        this.price = price;
    }

    public String getBookId() {
        return bookId;
    }

    public String getName() {
        return name;
    }

    public Float getPrice() {
        return price;
    }

}
