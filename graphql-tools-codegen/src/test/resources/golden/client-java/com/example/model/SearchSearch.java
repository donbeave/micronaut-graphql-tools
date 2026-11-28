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
        defaultImpl = SearchSearchOther.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SearchSearchFolder.class, name = "Folder"),
        @JsonSubTypes.Type(value = SearchSearchFile.class, name = "File"),
        @JsonSubTypes.Type(value = SearchSearchUser.class, name = "User")
})
public sealed interface SearchSearch permits SearchSearchFolder, SearchSearchFile, SearchSearchUser, SearchSearchOther {
    String __typename();
}
