package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class Filter {

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

}
