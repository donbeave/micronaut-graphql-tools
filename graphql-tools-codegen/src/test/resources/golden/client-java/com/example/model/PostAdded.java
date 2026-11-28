package com.example.model;

import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The request of the subscription {@code PostAdded}: its document, its variables and the types of its result. It sends nothing.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public final class PostAdded {
    /**
     * The name of the operation.
     */
    public static final String NAME = "PostAdded";

    /**
     * The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it.
     */
    public static final String DOCUMENT = "subscription PostAdded{postAdded{id likes @skip(if:false)}}";

    /**
     * The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits.
     */
    public static final String HASH = "77505f5df8fa96e12de3a8337049b1906fe7a98dec0a867a3991449733e6b025";

    /**
     * The kind of the operation.
     */
    public static final OperationType TYPE = OperationType.SUBSCRIPTION;

    /**
     * The response key of the one root field that the operation selects.
     */
    public static final String FIELD = "postAdded";

    /**
     * The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.
     */
    public static final Argument<PostAddedPostAdded> RESULT = Argument.of(PostAddedPostAdded.class);

    /**
     * The type that the {@code data} object of the response is read as: a map from the response key of the root field to its value.
     */
    public static final Argument<Map<String, PostAddedPostAdded>> DATA = Argument.mapOf(Argument.STRING, RESULT);

    private final PostAddedVariables variables;

    public PostAdded(PostAddedVariables variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    public PostAdded() {
        this(new PostAddedVariables());
    }

    /**
     * The variables of this request.
     */
    public PostAddedVariables variables() {
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
        return this == other || other instanceof PostAdded that && variables.equals(that.variables);
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
