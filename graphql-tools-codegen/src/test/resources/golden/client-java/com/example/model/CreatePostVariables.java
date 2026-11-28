package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

/**
 * The variables of the operation {@code CreatePost}.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record CreatePostVariables(
        String title,
        @Nullable String body
) {
}
