package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

import java.util.List;

@GraphQLInput
public class BookFilter {

    private String title;
    private List<Genre> genres;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public void setGenres(List<Genre> genres) {
        this.genres = genres;
    }

}
