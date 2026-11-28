package com.example.model;

import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The request of the query {@code GetUser}: its document, its variables and the types of its result. It sends nothing.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public final class GetUser {
    /**
     * The name of the operation.
     */
    public static final String NAME = "GetUser";

    /**
     * The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it.
     */
    public static final String DOCUMENT = "query GetUser($id:ID!,$withPosts:Boolean=false,$first:Int){user(id:$id){...UserFields posts(first:$first) @include(if:$withPosts){id title likes}}} fragment UserFields on User {id name role}";

    /**
     * The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits.
     */
    public static final String HASH = "fbd78ac8c2160addfb4704f3fee61f3534b2dc19c894d811b1804de9d3db6bb0";

    /**
     * The kind of the operation.
     */
    public static final OperationType TYPE = OperationType.QUERY;

    /**
     * The response key of the one root field that the operation selects.
     */
    public static final String FIELD = "user";

    /**
     * The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.
     */
    public static final Argument<GetUserUser> RESULT = Argument.of(GetUserUser.class);

    /**
     * The type that the {@code data} object of the response is read as: a map from the response key of the root field to its value.
     */
    public static final Argument<Map<String, GetUserUser>> DATA = Argument.mapOf(Argument.STRING, RESULT);

    private final GetUserVariables variables;

    public GetUser(GetUserVariables variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * The variables of this request.
     */
    public GetUserVariables variables() {
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
        return this == other || other instanceof GetUser that && variables.equals(that.variables);
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
