package io.micronaut.graphql.tools.mapping.input;

import io.micronaut.graphql.tools.ArgumentValue;
import io.micronaut.graphql.tools.annotation.GraphQLInput;

import java.util.List;

public final class JavaRecordInputs {

    private JavaRecordInputs() {
    }

    @GraphQLInput
    public record Plain(String name, int count, Integer limit) {
    }

    @GraphQLInput
    public record Line(String sku, ArgumentValue<String> note) {
    }

    @GraphQLInput
    public record Order(String id, Line first, List<Line> lines, ArgumentValue<String> memo) {
    }

    public static String show(ArgumentValue<String> value) {
        return value.isOmitted() ? "omitted" : value.isNull() ? "null" : "value(" + value.get() + ")";
    }

}
