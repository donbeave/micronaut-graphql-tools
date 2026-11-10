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
package io.micronaut.graphql.tools;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * The value of a nullable GraphQL argument or input object field that tells an omitted value from an explicit
 * {@code null}. Declare a resolver parameter or an input class property as {@code ArgumentValue<T>} instead of
 * {@code T}; this needs {@code micronaut.graphql.tools.input.detect-omission=true}.
 * <ul>
 *     <li>{@link #isOmitted()}: the client did not provide the value, and the schema declares no default for it;</li>
 *     <li>{@link #isNull()}: the client provided an explicit {@code null};</li>
 *     <li>{@link #isPresent()}: the client provided a value, or the schema's default applies.</li>
 * </ul>
 *
 * @param <T> the Java type of the value
 * @since 1.0.0
 */
public final class ArgumentValue<T> {

    private static final ArgumentValue<?> OMITTED = new ArgumentValue<>(true, null);
    private static final ArgumentValue<?> NULL = new ArgumentValue<>(false, null);

    private final boolean omitted;
    private final T value;

    private ArgumentValue(boolean omitted, @Nullable T value) {
        this.omitted = omitted;
        this.value = value;
    }

    /**
     * @param <T> the Java type of the value
     * @return a value the client did not provide
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> ArgumentValue<T> omitted() {
        return (ArgumentValue<T>) OMITTED;
    }

    /**
     * @param value the value the client provided, {@code null} for an explicit {@code null}
     * @param <T>   the Java type of the value
     * @return a provided value
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> ArgumentValue<T> ofNullable(@Nullable T value) {
        return value == null ? (ArgumentValue<T>) NULL : new ArgumentValue<>(false, value);
    }

    /**
     * @return whether the client did not provide the value
     */
    public boolean isOmitted() {
        return omitted;
    }

    /**
     * @return whether the client provided an explicit {@code null}
     */
    public boolean isNull() {
        return !omitted && value == null;
    }

    /**
     * @return whether the client provided a value other than {@code null}
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * @return the provided value, {@code null} if it is omitted or an explicit {@code null}
     */
    @Nullable
    public T getValue() {
        return value;
    }

    /**
     * @return the provided value
     * @throws NoSuchElementException if the value is omitted or an explicit {@code null}
     */
    @NonNull
    public T get() {
        if (value == null) {
            throw new NoSuchElementException(omitted ? "The value is omitted" : "The value is null");
        }
        return value;
    }

    /**
     * @param other the value to return if no value other than {@code null} is provided
     * @return the provided value or {@code other}
     */
    @Nullable
    public T orElse(@Nullable T other) {
        return value != null ? value : other;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArgumentValue<?> that)) {
            return false;
        }
        return omitted == that.omitted && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(omitted, value);
    }

    @Override
    public String toString() {
        if (omitted) {
            return "ArgumentValue.omitted";
        }
        return "ArgumentValue[" + value + "]";
    }

}
