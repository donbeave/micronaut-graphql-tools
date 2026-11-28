package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record ViewerData(
        ViewerViewer viewer,
        ViewerMine mine
) {
}
