package com.example.model;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
public record Pet(
        String name,
        @Nullable String nickname,
        @Nullable List<@Nullable String> tags
) {
}
