package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book {

    private final String title;

    public Book(String title) {
        this.title = title;
    }

    public String getIsbn() {
        return null;
    }

    public String getTitle() {
        return title;
    }

}
