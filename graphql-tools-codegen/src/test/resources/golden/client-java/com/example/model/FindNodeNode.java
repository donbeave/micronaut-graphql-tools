package com.example.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "__typename",
        visible = true,
        defaultImpl = FindNodeNodeOther.class
)
@JsonSubTypes(@JsonSubTypes.Type(value = FindNodeNodeFolder.class, name = "Folder"))
public sealed interface FindNodeNode permits FindNodeNodeFolder, FindNodeNodeOther {
    String __typename();

    String id();
}
