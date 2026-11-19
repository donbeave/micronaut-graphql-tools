package example

import io.micronaut.core.annotation.Creator
import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Filter {

    private final String text

    private Filter(String text) {
        this.text = text
    }

    @Creator
    static Filter of(String text) {
        return new Filter(text)
    }

    String getText() {
        return text
    }

}
