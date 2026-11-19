package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Filter {

    private final String text

    Filter(String text) {
        this.text = text
    }

    String getText() {
        return text
    }

}
