package com.example.model;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
@AccessorsStyle(
        readPrefixes = ""
)
public sealed interface Node permits Pet {
    String id();
}
