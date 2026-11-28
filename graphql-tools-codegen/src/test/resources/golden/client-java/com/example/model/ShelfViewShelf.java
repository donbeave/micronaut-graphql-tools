package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record ShelfViewShelf(
        String name,
        List<ShelfViewShelfBooks> books,
        @Nullable List<@Nullable List<@Nullable ShelfViewShelfRows>> rows
) {
}
