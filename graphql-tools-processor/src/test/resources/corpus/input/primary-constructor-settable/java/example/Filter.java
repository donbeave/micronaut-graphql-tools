package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class Filter {

    private final String text;

    public Filter(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

}
