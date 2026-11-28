package com.example.model;

import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The request of the mutation {@code CreatePost}: its document, its variables and the types of its result. It sends nothing.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public final class CreatePost {
    /**
     * The name of the operation.
     */
    public static final String NAME = "CreatePost";

    /**
     * The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it.
     */
    public static final String DOCUMENT = "mutation CreatePost($title:String!,$body:String){createPost(title:$title,body:$body){id title}}";

    /**
     * The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits.
     */
    public static final String HASH = "4fa904ebbd697fb3aa7ea29b6fe7286f1994e1d64cbd4dce7978774c9c27391f";

    /**
     * The kind of the operation.
     */
    public static final OperationType TYPE = OperationType.MUTATION;

    /**
     * The response key of the one root field that the operation selects.
     */
    public static final String FIELD = "createPost";

    /**
     * The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.
     */
    public static final Argument<CreatePostCreatePost> RESULT = Argument.of(CreatePostCreatePost.class);

    /**
     * The type that the {@code data} object of the response is read as: a map from the response key of the root field to its value.
     */
    public static final Argument<Map<String, CreatePostCreatePost>> DATA = Argument.mapOf(Argument.STRING, RESULT);

    private final CreatePostVariables variables;

    public CreatePost(CreatePostVariables variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * The variables of this request.
     */
    public CreatePostVariables variables() {
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
        return this == other || other instanceof CreatePost that && variables.equals(that.variables);
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
