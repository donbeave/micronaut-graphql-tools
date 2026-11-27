package example;

import java.util.List;

public record BookFilter(String title, List<String> tags, Genre genre, int limit, BookFilter nested) {
}
