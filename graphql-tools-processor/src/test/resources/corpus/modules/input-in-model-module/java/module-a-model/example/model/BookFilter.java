package example.model;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class BookFilter {

    private String title;
    private String extra;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

}
