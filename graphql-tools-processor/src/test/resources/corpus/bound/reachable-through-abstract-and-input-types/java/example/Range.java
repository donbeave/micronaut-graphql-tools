package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class Range {

    private Integer from;

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

}
