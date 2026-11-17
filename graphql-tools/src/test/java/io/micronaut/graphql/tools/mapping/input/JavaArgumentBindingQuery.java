package io.micronaut.graphql.tools.mapping.input;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLArgument;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

/**
 * A Java resolver for {@code ArgumentBindingSpec}. The parameters are declared in a different order than the SDL
 * arguments; the test build does not pass {@code -parameters}, so the names come from Micronaut's compile-time
 * metadata.
 */
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
