package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.time.OffsetDateTime;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record PostFilter(
        @Nullable String text,
        @Nullable Integer minLikes,
        @Nullable Role role,
        @Nullable OffsetDateTime since,
        @Nullable List<String> tags
) {
}
