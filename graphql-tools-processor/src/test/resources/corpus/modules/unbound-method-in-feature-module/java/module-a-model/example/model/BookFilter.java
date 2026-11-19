package example.model;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class BookFilter {

    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}
