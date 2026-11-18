package example;

import io.micronaut.core.annotation.Creator;
import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class Filter {

    private final String text;

    private Filter(String text) {
        this.text = text;
    }

    @Creator
    public static Filter of(String text) {
        return new Filter(text);
    }

    public String getText() {
        return text;
    }

}
