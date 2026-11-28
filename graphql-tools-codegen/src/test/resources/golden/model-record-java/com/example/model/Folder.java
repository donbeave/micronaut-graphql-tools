package com.example.model;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
public record Folder(
        String id,
        List<Node> children
) implements Node, SearchResult {
}
