package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record GetBookBookEditions(
        int year,
        @Nullable String label,
        @Nullable List<List<String>> isbns
) implements EditionLine {
}
