package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.time.OffsetDateTime;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record SearchSearchFolder(
        String __typename,
        String id,
        String name,
        OffsetDateTime createdAt
) implements SearchSearch {
}
