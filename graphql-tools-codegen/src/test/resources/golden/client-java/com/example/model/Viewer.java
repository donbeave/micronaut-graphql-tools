package com.example.model;

import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The request of the query {@code Viewer}: its document, its variables and the types of its result. It sends nothing.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public final class Viewer {
    /**
     * The name of the operation.
     */
    public static final String NAME = "Viewer";

    /**
     * The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it.
     */
    public static final String DOCUMENT = "query Viewer{viewer{id tags}mine:viewer{name @skip(if:true) id}}";

    /**
     * The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits.
     */
    public static final String HASH = "76288754d3f486c1b9f9fc1ea09b1a3004649f44abd600e4994f8a9aff1e3cdf";

    /**
     * The kind of the operation.
     */
    public static final OperationType TYPE = OperationType.QUERY;

    /**
     * The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.
     */
    public static final Argument<ViewerData> RESULT = Argument.of(ViewerData.class);

    /**
     * The type that the {@code data} object of the response is read as.
     */
    public static final Argument<ViewerData> DATA = RESULT;

    private final ViewerVariables variables;

    public Viewer(ViewerVariables variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    public Viewer() {
        this(new ViewerVariables());
    }

    /**
     * The variables of this request.
     */
    public ViewerVariables variables() {
        return variables;
    }

    /**
     * The name of the operation.
     */
    public String name() {
        return NAME;
    }

    /**
     * The document of the operation.
     */
    public String document() {
        return DOCUMENT;
    }

    /**
     * The hash of the document.
     */
    public String hash() {
        return HASH;
    }

    /**
     * The kind of the operation.
     */
    public OperationType kind() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Viewer that && variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
        return variables.hashCode();
    }

    @Override
    public String toString() {
        return NAME + variables;
    }
}
