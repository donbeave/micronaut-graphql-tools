package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

/**
 * The variables of the operation {@code GetBook}.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record GetBookVariables(
        String id
) {
}
