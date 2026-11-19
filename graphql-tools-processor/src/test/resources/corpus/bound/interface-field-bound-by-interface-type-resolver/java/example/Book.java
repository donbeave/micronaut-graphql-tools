package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book implements Named {

    private final String title;

    public Book(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

}
