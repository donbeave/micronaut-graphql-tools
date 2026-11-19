package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

import java.util.List;

@GraphQLInput
public class BookFilter {

    private io.micronaut.graphql.tools.ArgumentValue<String> title;
    private List<Genre> genres;

    public io.micronaut.graphql.tools.ArgumentValue<String> getTitle() {
        return title;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public void setGenres(List<Genre> genres) {
        this.genres = genres;
    }

}
