package io.micronaut.graphql.tools.ignore;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;

@Introspected
public enum JavaIgnoreLevel {
    LOW,
    HIGH,
    @GraphQLIgnore
    INTERNAL_ONLY
}
