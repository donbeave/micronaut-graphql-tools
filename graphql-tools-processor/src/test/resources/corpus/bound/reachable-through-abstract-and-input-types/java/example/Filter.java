package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class Filter {

    private Range range;

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

}
