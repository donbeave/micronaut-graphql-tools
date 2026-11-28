package com.example.model;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import java.util.List;
import javax.annotation.processing.Generated;

/**
 * A node with a name.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
@AccessorsStyle(
        readPrefixes = ""
)
public sealed interface Named extends Node permits Product {
    String id();

    /**
     * The display name.
     */
    String name();

    @Nullable String nickname();

    List<Node> children();

    /**
     * @deprecated Use name.
     */
    @Deprecated
    @Nullable String oldName();

    @Nullable Integer count();
}
