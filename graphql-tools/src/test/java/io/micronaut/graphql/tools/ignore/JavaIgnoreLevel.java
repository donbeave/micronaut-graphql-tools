package io.micronaut.graphql.tools.ignore;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;

/**
 * An enum whose constant {@code INTERNAL_ONLY} has no value in the schema enum; its annotation is read from the bean
 * introspection of the enum.
 */
@Introspected
public enum JavaIgnoreLevel {
    LOW,
    HIGH,
    @GraphQLIgnore
    INTERNAL_ONLY
}
