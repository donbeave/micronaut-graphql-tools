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
package io.micronaut.graphql.tools.scalars.internal.time;

import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.scalars.internal.WireCoercing;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.function.Function;

/**
 * A scalar whose wire value is a string: the Java value is written with a formatter and read back with a parser.
 *
 * @param <T> the {@code java.time} type
 */
@Internal
final class TextCoercing<T> extends WireCoercing<T, String> {

    private final Class<T> type;
    private final Function<String, T> parser;
    private final Function<T, String> writer;
    private final String expected;

    TextCoercing(String scalarName, Class<T> type, Function<String, T> parser, Function<T, String> writer, String expected) {
        super(scalarName);
        this.type = type;
        this.parser = parser;
        this.writer = writer;
        this.expected = expected;
    }

    /**
     * @param scalarName the name of the scalar
     * @param type       the Java class
     * @param formatter  reads and writes the wire value
     * @param query      builds the value from the parsed fields
     * @param expected   what the wire value looks like, for the message
     * @param <T>        the Java type
     * @return the coercion
     */
    static <T> TextCoercing<T> of(String scalarName, Class<T> type, DateTimeFormatter formatter,
                                  TemporalQuery<T> query, String expected) {
        return new TextCoercing<>(scalarName, type, text -> formatter.parse(text, query),
                value -> formatter.format((TemporalAccessor) value), expected);
    }

    @Override
    protected String write(Object value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("expected a " + type.getName() + ", but was " + describe(value));
        }
        return writer.apply(type.cast(value));
    }

    @Override
    protected T read(Object input) {
        if (input instanceof String) {
            return parse((String) input);
        }
        if (type.isInstance(input)) {
            return type.cast(input);
        }
        throw new IllegalArgumentException("expected a string " + expected + ", but was " + describe(input));
    }

    @Override
    protected T readLiteral(Value<?> literal, CoercedVariables variables) {
        if (literal instanceof StringValue) {
            return parse(((StringValue) literal).getValue());
        }
        throw new IllegalArgumentException("expected a string " + expected + ", but was " + literal.getClass().getSimpleName());
    }

    private T parse(String text) {
        try {
            return parser.apply(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("'" + text + "' is not valid, expected " + expected, e);
        }
    }

}
