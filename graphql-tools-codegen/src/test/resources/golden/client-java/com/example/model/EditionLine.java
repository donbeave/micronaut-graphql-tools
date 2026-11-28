package com.example.model;

import java.util.List;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

@Generated("io.micronaut.graphql.tools.codegen")
public interface EditionLine {
    int year();

    @Nullable String label();

    @Nullable List<List<String>> isbns();
}
