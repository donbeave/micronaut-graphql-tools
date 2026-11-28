package com.example.model;

import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The request of the query {@code Books}: its document, its variables and the types of its result. It sends nothing.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public final class Books {
    /**
     * The name of the operation.
     */
    public static final String NAME = "Books";

    /**
     * The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it.
     */
    public static final String DOCUMENT = "query Books($status:Status,$first:Int!=10,$withAuthor:Boolean!){books(status:$status,first:$first){id status author @include(if:$withAuthor){name}...on Book{rating @skip(if:false)}}}";

    /**
     * The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits.
     */
    public static final String HASH = "47f95d80c4a6e4b43387d8ceea6afef694000ff5d0cb0a9cb68053c326ff4c60";

    /**
     * The kind of the operation.
     */
    public static final OperationType TYPE = OperationType.QUERY;

    /**
     * The response key of the one root field that the operation selects.
     */
    public static final String FIELD = "books";

    /**
     * The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.
     */
    public static final Argument<List<BooksBooks>> RESULT = Argument.listOf(BooksBooks.class);

    /**
     * The type that the {@code data} object of the response is read as: a map from the response key of the root field to its value.
     */
    public static final Argument<Map<String, List<BooksBooks>>> DATA = Argument.mapOf(Argument.STRING, RESULT);

    private final BooksVariables variables;

    public Books(BooksVariables variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * The variables of this request.
     */
    public BooksVariables variables() {
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
        return this == other || other instanceof Books that && variables.equals(that.variables);
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
