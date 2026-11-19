package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

import java.util.List;

@GraphQLInput
public class BookFilter {

    private final String title;
    private final List<Genre> genres;
    private final String locale;

    public BookFilter(String title, List<Genre> genres, String locale) {
        this.title = title;
        this.genres = genres;
        this.locale = locale;
    }

    public String getTitle() {
        return title;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isEmpty() {
        return title == null;
    }

}
