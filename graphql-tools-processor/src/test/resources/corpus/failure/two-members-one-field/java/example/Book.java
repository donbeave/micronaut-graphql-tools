package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book {

    private final String title;

    public Book(String title) {
        this.title = title;
    }

    @io.micronaut.graphql.tools.annotation.GraphQLField
    public String title() {
        return title;
    }

    public String getTitle() {
        return title;
    }

}
