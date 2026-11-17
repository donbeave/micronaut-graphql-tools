package io.micronaut.graphql.tools.mapping.input;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLArgument;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Requires(property = "spec.name", value = "ArgumentBindingSpecJava")
@GraphQLRootResolver
public class JavaArgumentBindingQuery {

    public String page(Integer offset, Integer first) {
        return "first=" + first + " offset=" + offset;
    }

    public String renamed(@GraphQLArgument("long") Integer length, @GraphQLArgument("short") Integer width) {
        return "long=" + length + " short=" + width;
    }

}
