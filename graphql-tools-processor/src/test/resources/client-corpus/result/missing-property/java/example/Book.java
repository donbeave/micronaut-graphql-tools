package example;

public record Book(String id, String title, Integer rating, Author author, Genre genre, java.util.List<String> tags) {
}
