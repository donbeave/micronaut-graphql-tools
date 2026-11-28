package com.example.model;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Introspected
@AccessorsStyle(
        readPrefixes = ""
)
public sealed interface Node permits Product, Folder {
    String id();

    List<Node> children();
}
