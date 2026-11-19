package example.model;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book {

    private final String id;
    private final String title;

    public Book(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

}
