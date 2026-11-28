package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

/**
 * The variables of the operation {@code Search}.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record SearchVariables(
        String text,
        @Nullable PostFilter filter
) {
}
