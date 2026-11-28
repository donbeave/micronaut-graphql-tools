package com.example.model;

import io.micronaut.serde.annotation.Serdeable;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable
public record GetUserUserPosts(
        String id,
        String title,
        int likes
) {
}
