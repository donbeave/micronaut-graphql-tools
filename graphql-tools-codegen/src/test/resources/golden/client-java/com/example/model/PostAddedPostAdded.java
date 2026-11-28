package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record PostAddedPostAdded(
        String id,
        @Nullable Integer likes
) {
}
