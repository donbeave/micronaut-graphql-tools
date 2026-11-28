package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

/**
 * What the library needs to add a book.
 *
 * @param title The title of the book.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record BookInput(
        String title,
        @Nullable String subtitle,
        @Nullable Integer pages,
        @Nullable List<String> tags,
        @Nullable Status status,
        @Nullable BookInput nested
) {
}
