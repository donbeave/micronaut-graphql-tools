package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

/**
 * The object types that the selection does not name; the field {@code __typename} holds the name of the type.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record SearchSearchOther(
        String __typename
) implements SearchSearch {
}
