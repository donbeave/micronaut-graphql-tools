package com.example.model;

import javax.annotation.processing.Generated;

/**
 * A colour of a thing.
 * Ends a comment with *&#47; and names a tag &#64;see here.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public enum Color {

    /**
     * The red one
     */
    RED,
    /**
     * @deprecated Use RED
     */
    @Deprecated
    GREEN,
    /**
     * @deprecated No longer supported
     */
    @Deprecated
    BLUE,
    /**
     * Added later
     */
    BLACK
}
