package example.data;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class BookSellerRepository {

    private static final List<BookSeller> STATE = new ArrayList<>();

    static {
        STATE.add(new BookSeller("ab", "Amaz", 20.00f));
        STATE.add(new BookSeller("ab", "Yah", 21.00f));
        STATE.add(new BookSeller("ax", "Amaz", 19.00f));
        STATE.add(new BookSeller("gd", "Zap", 19.50f));
        STATE.add(new BookSeller("we", "Amaz", 36.00f));
        STATE.add(new BookSeller("we", "Yah", 35.00f));
        STATE.add(new BookSeller("we", "Zap", 34.00f));
    }

    public List<BookSeller> findAllByBookIds(Collection<String> bookIds) {
        return STATE.stream()
                .filter(it -> bookIds.contains(it.getBookId()))
                .collect(Collectors.toList());
    }

}
