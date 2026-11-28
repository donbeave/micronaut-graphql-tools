package com.example.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
@GraphQLType(
        name = "Owner"
)
public record OwnerModel(
        @Nullable String name
) {
}
