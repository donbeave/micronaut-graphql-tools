package com.example.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;
import javax.annotation.processing.Generated;

/**
 * An item that is for sale.
 * Ends a comment with *&#47; here.
 *
 * @param name The display name.
 * @param oldName Deprecated: Use name.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
public record Product(
        String id,
        String name,
        @Nullable String nickname,
        Integer count,
        Double ratio,
        @Nullable Boolean flag,
        int stars,
        double score,
        boolean active,
        @Nullable Integer maybeStars,
        List<String> tags,
        @Nullable List<@Nullable String> aliases,
        @Nullable List<List<Integer>> grid,
        Color color,
        @Nullable Color favorite,
        @Nullable LocalDate released,
        @Deprecated @Nullable String oldName,
        List<Node> children
) implements Node, Counted, SearchResult {
}
