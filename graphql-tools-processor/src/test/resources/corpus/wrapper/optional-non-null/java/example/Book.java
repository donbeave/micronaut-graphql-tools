package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book {

    private final String title;

    public Book(String title) {
        this.title = title;
    }

    public java.util.Optional<String> getTitle() {
        return java.util.Optional.of(title);
    }

}
