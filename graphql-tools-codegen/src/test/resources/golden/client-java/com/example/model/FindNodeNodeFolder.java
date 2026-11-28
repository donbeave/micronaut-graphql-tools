package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record FindNodeNodeFolder(
        String __typename,
        String id,
        String name,
        int size,
        List<FolderBitsChildren> children
) implements FindNodeNode, FolderBits {
}
