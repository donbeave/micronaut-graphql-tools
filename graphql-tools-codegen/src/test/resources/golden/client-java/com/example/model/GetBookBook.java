package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

/**
 * @param title The title of the book.
 * @param oldTitle Deprecated: Use title.
 * @param headline The title of the book.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record GetBookBook(
        String id,
        String title,
        List<@Nullable String> tags,
        boolean inStock,
        double rating,
        @Nullable String subtitle,
        Status status,
        @Deprecated @Nullable String oldTitle,
        String headline,
        @Nullable List<GetBookBookEditions> editions
) implements BookCard, BookBadge {
}
