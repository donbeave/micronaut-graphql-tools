package example.data;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class UserBookRepository {

    private static final Map<Long, List<Book>> STATE = new HashMap<>();

    public List<Book> findAllByUserId(@NonNull User user) {
        ArgumentUtils.requireNonNull("user", user);

        return STATE.getOrDefault(user.getId(), Collections.emptyList());
    }

    public void addBook(@NonNull User user, @NonNull Book book) {
        STATE.putIfAbsent(user.getId(), new ArrayList<>());
        if (STATE.get(user.getId()).contains(book)) {
            throw new DuplicateException();
        }
        STATE.get(user.getId()).add(book);
    }

    public static class DuplicateException extends RuntimeException {
    }

}
