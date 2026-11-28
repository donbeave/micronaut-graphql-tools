package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record GetUserUser(
        String id,
        String name,
        Role role,
        @Nullable List<GetUserUserPosts> posts
) implements UserFields {
}
