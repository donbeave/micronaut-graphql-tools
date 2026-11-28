package com.example.model;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
@AccessorsStyle(
        readPrefixes = ""
)
public sealed interface Counted permits Product {
    @Nullable Integer count();

    @Nullable Double ratio();

    @Nullable Boolean flag();
}
