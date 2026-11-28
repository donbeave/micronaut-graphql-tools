package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

/**
 * @param title The title of the book.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record AddBookAddBook(
        String id,
        String title
) {
}
