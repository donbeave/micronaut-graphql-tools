package com.example.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
public record Pet(
        String id,
        @Nullable String v2Api,
        @Nullable Integer HTTPStatus
) implements Node, Thing {
}
