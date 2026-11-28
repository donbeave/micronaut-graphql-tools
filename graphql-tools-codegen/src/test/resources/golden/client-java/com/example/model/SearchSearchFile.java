package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record SearchSearchFile(
        String __typename,
        String id,
        int bytes
) implements SearchSearch {
}
