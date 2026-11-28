package com.example.model;

import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The request of the query {@code FindNode}: its document, its variables and the types of its result. It sends nothing.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public final class FindNode {
    /**
     * The name of the operation.
     */
    public static final String NAME = "FindNode";

    /**
     * The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it.
     */
    public static final String DOCUMENT = "query FindNode($id:ID!){node(id:$id){__typename id ...FolderBits}} fragment FolderBits on Folder {name size children{__typename id}}";

    /**
     * The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits.
     */
    public static final String HASH = "28cb04fe072e04f75d44f091fb759034992504a9d65757a62b8c1697d011206f";

    /**
     * The kind of the operation.
     */
    public static final OperationType TYPE = OperationType.QUERY;

    /**
     * The response key of the one root field that the operation selects.
     */
    public static final String FIELD = "node";

    /**
     * The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.
     */
    public static final Argument<FindNodeNode> RESULT = Argument.of(FindNodeNode.class);

    /**
     * The type that the {@code data} object of the response is read as: a map from the response key of the root field to its value.
     */
    public static final Argument<Map<String, FindNodeNode>> DATA = Argument.mapOf(Argument.STRING, RESULT);

    private final FindNodeVariables variables;

    public FindNode(FindNodeVariables variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * The variables of this request.
     */
    public FindNodeVariables variables() {
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
        return this == other || other instanceof FindNode that && variables.equals(that.variables);
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
