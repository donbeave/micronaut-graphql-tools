package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class BookFilter {

    private final String title
    private final List<Genre> genres
    private final String locale

    BookFilter(String title, List<Genre> genres, String locale) {
        this.title = title
        this.genres = genres
        this.locale = locale
    }

    String getTitle() {
        return title
    }

    List<Genre> getGenres() {
        return genres
    }

    String getLocale() {
        return locale
    }

    boolean isEmpty() {
        return title == null
    }

}
