package com.example.model;

import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
public interface BookCard {
    String id();

    String title();

    List<@Nullable String> tags();

    boolean inStock();

    double rating();
}
