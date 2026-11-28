package com.example.model;

import java.util.List;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
public interface FolderBits {
    String name();

    int size();

    List<FolderBitsChildren> children();
}
