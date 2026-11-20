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
import graphql.language.IntValue;
import graphql.language.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.scalars.internal.WireCoercing;

import java.math.BigInteger;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * A scalar whose wire value is a whole number: a count of seconds or milliseconds.
 *
 * @param <T> the {@code java.time} type
 */
@Internal
final class CountCoercing<T> extends WireCoercing<T, Long> {

    private final Class<T> type;
    private final ToLongFunction<T> writer;
    private final Function<Long, T> reader;
    private final String expected;

    CountCoercing(String scalarName, Class<T> type, ToLongFunction<T> writer, Function<Long, T> reader, String expected) {
        super(scalarName);
        this.type = type;
        this.writer = writer;
        this.reader = reader;
        this.expected = expected;
    }

    @Override
    protected Long write(Object value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("cannot write " + describe(value) + ", expected a value to write as " + expected);
        }
        return writer.applyAsLong(type.cast(value));
    }

    @Override
    protected T read(Object input) {
        if (type.isInstance(input)) {
            return type.cast(input);
        }
        if (input instanceof Long || input instanceof Integer || input instanceof Short || input instanceof Byte) {
            return build(((Number) input).longValue());
        }
        if (input instanceof BigInteger) {
            return build(exact((BigInteger) input));
        }
        throw new IllegalArgumentException("expected " + expected + ", but was " + describe(input));
    }

    @Override
    protected T readLiteral(Value<?> literal, CoercedVariables variables) {
        if (literal instanceof IntValue) {
            return build(exact(((IntValue) literal).getValue()));
        }
        throw new IllegalArgumentException("expected " + expected + ", but was " + describeLiteral(literal));
    }

    private long exact(BigInteger value) {
        if (value.bitLength() >= Long.SIZE) {
            throw new IllegalArgumentException(value + " is out of range, expected " + expected);
        }
        return value.longValue();
    }

    private T build(long count) {
        try {
            return reader.apply(count);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(count + " is out of range, expected " + expected, e);
        }
    }

}
