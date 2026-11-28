package com.example.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "__typename",
        visible = true,
        defaultImpl = FolderBitsChildrenOther.class
)
public sealed interface FolderBitsChildren permits FolderBitsChildrenOther {
    String __typename();

    String id();
}
