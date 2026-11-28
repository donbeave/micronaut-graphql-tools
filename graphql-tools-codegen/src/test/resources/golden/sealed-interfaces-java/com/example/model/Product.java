package com.example.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
public record Product(
        String id,
        String name,
        @Nullable String nickname,
        List<Node> children,
        @Nullable String oldName,
        Integer count
) implements Node, Named, SearchResult, Media {
}
