/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.scalars.internal;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import io.micronaut.core.annotation.Internal;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;

/**
 * The shared shape of the coercions of this module: three hooks that read and write the wire value, and the translation of a
 * failure of a hook into the graphql-java exception of the phase, so that every scalar reports an invalid value the same way.
 * Both generations of the {@link Coercing} methods are implemented, so the coercion works on every supported graphql-java.
 *
 * @param <T> the Java type the scalar parses to
 * @param <O> the wire type the scalar serializes to
 */
@Internal
public abstract class WireCoercing<T, O> implements Coercing<T, O> {

    private final String scalarName;

    protected WireCoercing(String scalarName) {
        this.scalarName = scalarName;
    }

    /**
     * @param value the value a data fetcher returned
     * @return the wire value
     * @throws RuntimeException when the value cannot be written; the message says why
     */
    protected abstract O write(Object value);

    /**
     * @param input a variable value, as parsed from the request
     * @return the Java value
     * @throws RuntimeException when the value is not valid; the message says why
     */
    protected abstract T read(Object input);

    /**
     * @param literal   a literal of the query document
     * @param variables the coerced variables of the request
     * @return the Java value
     * @throws RuntimeException when the literal is not valid; the message says why
     */
    protected abstract T readLiteral(Value<?> literal, CoercedVariables variables);

    protected final String scalarName() {
        return scalarName;
    }

    @Override
    public final O serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale) {
        try {
            return write(dataFetcherResult);
        } catch (RuntimeException e) {
            throw new CoercingSerializeException(scalarName + ": " + e.getMessage(), e);
        }
    }

    @Override
    public final O serialize(Object dataFetcherResult) {
        return serialize(dataFetcherResult, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Override
    public final T parseValue(Object input, GraphQLContext graphQLContext, Locale locale) {
        try {
            return read(input);
        } catch (RuntimeException e) {
            throw new CoercingParseValueException(scalarName + ": " + e.getMessage(), e);
        }
    }

    @Override
    public final T parseValue(Object input) {
        return parseValue(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Override
    public final T parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        try {
            return readLiteral(input, variables);
        } catch (RuntimeException e) {
            throw new CoercingParseLiteralException(scalarName + ": " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public final T parseLiteral(Object input) {
        return parseLiteral(input, Map.of());
    }

    @Override
    @SuppressWarnings("deprecation")
    public final T parseLiteral(Object input, Map<String, Object> variables) {
        if (!(input instanceof Value)) {
            throw new CoercingParseLiteralException(scalarName + ": expected a literal, but was " + describe(input));
        }
        return parseLiteral((Value<?>) input, CoercedVariables.of(variables), GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Override
    @SuppressWarnings({"rawtypes", "deprecation"})
    public final Value valueToLiteral(Object input) {
        return valueToLiteral(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        Object wire = serialize(input, graphQLContext, locale);
        if (wire instanceof Long || wire instanceof Integer) {
            return IntValue.newIntValue(BigInteger.valueOf(((Number) wire).longValue())).build();
        }
        return StringValue.of(String.valueOf(wire));
    }

    /**
     * @param value any value, including {@code null}
     * @return a short description of the value for a message: the class, and the text of a string
     */
    protected static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence) {
            return "'" + value + "'";
        }
        return value.getClass().getSimpleName();
    }

}
