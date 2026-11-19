package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book {

    private final String title;

    public Book(String title) {
        this.title = title;
    }

    public Long getTitle() {
        return 1L;
    }

}
