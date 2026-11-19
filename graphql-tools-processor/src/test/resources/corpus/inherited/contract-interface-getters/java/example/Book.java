package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(BookGraph.class)
public class Book implements BookGraph {

    public String getTitle() {
        return null;
    }

    public Genre getGenre() {
        return null;
    }

    public String getIsbn() {
        return null;
    }

}
