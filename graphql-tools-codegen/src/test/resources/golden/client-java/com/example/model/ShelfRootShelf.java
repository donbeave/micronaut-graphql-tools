package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record ShelfRootShelf(
        String name,
        List<ShelfRootShelfBooks> books,
        @Nullable List<@Nullable List<@Nullable ShelfRootShelfRows>> rows
) {
}
