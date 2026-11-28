package com.example.model;

import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The request of the query {@code GetBook}: its document, its variables and the types of its result. It sends nothing.
 */
@Generated("io.micronaut.graphql.tools.codegen")
public final class GetBook {
    /**
     * The name of the operation.
     */
    public static final String NAME = "GetBook";

    /**
     * The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it.
     */
    public static final String DOCUMENT = "query GetBook($id:ID!){book(id:$id){...BookCard subtitle status oldTitle headline:title editions{...EditionLine}}} fragment BookBadge on Book {inStock rating} fragment BookCard on Book {id title tags ...BookBadge} fragment EditionLine on Edition {year label isbns}";

    /**
     * The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits.
     */
    public static final String HASH = "7496e4a8e586df92c63de799756e012d712e01b62ed38694ee9980e079fa1a89";

    /**
     * The kind of the operation.
     */
    public static final OperationType TYPE = OperationType.QUERY;

    /**
     * The response key of the one root field that the operation selects.
     */
    public static final String FIELD = "book";

    /**
     * The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.
     */
    public static final Argument<GetBookBook> RESULT = Argument.of(GetBookBook.class);

    /**
     * The type that the {@code data} object of the response is read as: a map from the response key of the root field to its value.
     */
    public static final Argument<Map<String, GetBookBook>> DATA = Argument.mapOf(Argument.STRING, RESULT);

    private final GetBookVariables variables;

    public GetBook(GetBookVariables variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * The variables of this request.
     */
    public GetBookVariables variables() {
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
        return this == other || other instanceof GetBook that && variables.equals(that.variables);
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
