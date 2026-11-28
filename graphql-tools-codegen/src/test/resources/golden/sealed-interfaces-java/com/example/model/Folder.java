package com.example.model;

import io.micronaut.core.annotation.Introspected;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
public record Folder(
        String id
) implements Node, SearchResult {
}
