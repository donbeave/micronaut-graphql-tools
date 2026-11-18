package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;

@Introspected
public enum Color {
    RED, GREEN,
    @GraphQLIgnore
    BLUE
}
