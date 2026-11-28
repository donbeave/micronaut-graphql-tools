package com.example.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
@GraphQLType(
        name = "Pet"
)
public record PetModel(
        OwnerModel owner
) {
}
