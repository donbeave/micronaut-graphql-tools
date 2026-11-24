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
package io.micronaut.graphql.tools.testing;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The response of one executed document: the data, the errors and the extensions, with assertions that return the response, so
 * they chain. A failed assertion throws an {@link AssertionError} that names the expectation and prints the response, so any test
 * framework reports it; the module depends on none.
 *
 * <p>A path is a dotted chain of field names and list indexes in the data, for example {@code book.authors[0].name}. A field name
 * is the response key, so an alias is the name to use.</p>
 */
public final class GraphQLTestResponse {

    private static final Object MISSING = new Object();

    private final ExecutionResult result;

    GraphQLTestResponse(ExecutionResult result) {
        this.result = result;
    }

    /**
     * @return the {@code data} of the response, or {@code null} when the operation produced none (a validation error)
     */
    @Nullable
    public Map<String, Object> getData() {
        return result.getData();
    }

    /**
     * @return the errors of the response, empty when there are none
     */
    @NonNull
    public List<GraphQLError> getErrors() {
        return result.getErrors();
    }

    /**
     * @return the {@code extensions} of the response, or {@code null} when there are none
     */
    @Nullable
    public Map<Object, Object> getExtensions() {
        return result.getExtensions();
    }

    /**
     * @return the result of graphql-java that this response wraps
     */
    @NonNull
    public ExecutionResult getResult() {
        return result;
    }

    /**
     * Reads a value of the data.
     *
     * @param path the path of the value
     * @return the value, or empty when the path does not exist or its value is {@code null}
     */
    @NonNull
    public Optional<Object> path(@NonNull String path) {
        Object value = resolve(path);
        return value == MISSING ? Optional.empty() : Optional.ofNullable(value);
    }

    /**
     * Reads a value of the data as a type.
     *
     * @param path the path of the value
     * @param type the expected type of the value
     * @param <T>  the type
     * @return the value, or empty when the path does not exist or its value is {@code null}
     * @throws AssertionError when the value is not of the type
     */
    @NonNull
    public <T> Optional<T> path(@NonNull String path, @NonNull Class<T> type) {
        Optional<Object> value = path(path);
        if (value.isPresent() && !type.isInstance(value.get())) {
            throw fail("the value at " + path + " is a " + value.get().getClass().getName() + ", not a " + type.getName());
        }
        return value.map(type::cast);
    }

    /**
     * @return this response, when it has no errors
     * @throws AssertionError when it has an error
     */
    @NonNull
    public GraphQLTestResponse assertNoErrors() {
        if (!getErrors().isEmpty()) {
            throw fail("expected no errors");
        }
        return this;
    }

    /**
     * @param count the expected number of errors
     * @return this response
     * @throws AssertionError when the number differs
     */
    @NonNull
    public GraphQLTestResponse assertErrorCount(int count) {
        if (getErrors().size() != count) {
            throw fail("expected " + count + " error(s)");
        }
        return this;
    }

    /**
     * @param expected the expected value of the path; a {@code Map} or a {@code List} is compared with {@code equals}
     * @param path     the path of the value
     * @return this response
     * @throws AssertionError when the path does not exist or has another value
     */
    @NonNull
    public GraphQLTestResponse assertPath(@NonNull String path, @Nullable Object expected) {
        Object value = resolve(path);
        if (value == MISSING) {
            throw fail("expected " + path + " to be " + expected + ", but the path does not exist");
        }
        if (!Objects.equals(value, expected)) {
            throw fail("expected " + path + " to be " + expected + ", but it is " + value);
        }
        return this;
    }

    /**
     * @param path the path of the value
     * @return this response, when the path exists and its value is {@code null}
     * @throws AssertionError otherwise
     */
    @NonNull
    public GraphQLTestResponse assertPathNull(@NonNull String path) {
        return assertPath(path, null);
    }

    /**
     * @param path the path of the value
     * @return this response, when the path exists and its value is not {@code null}
     * @throws AssertionError otherwise
     */
    @NonNull
    public GraphQLTestResponse assertPathPresent(@NonNull String path) {
        Object value = resolve(path);
        if (value == MISSING || value == null) {
            throw fail("expected " + path + " to have a value");
        }
        return this;
    }

    /**
     * @param part the text that the message of an error contains
     * @return this response, when an error has such a message
     * @throws AssertionError otherwise
     */
    @NonNull
    public GraphQLTestResponse assertErrorMessage(@NonNull String part) {
        for (GraphQLError error : getErrors()) {
            if (error.getMessage() != null && error.getMessage().contains(part)) {
                return this;
            }
        }
        throw fail("expected an error whose message contains \"" + part + "\"");
    }

    /**
     * @param path the path of the field of the error, in the form of a data path
     * @return this response, when an error has this path
     * @throws AssertionError otherwise
     */
    @NonNull
    public GraphQLTestResponse assertErrorPath(@NonNull String path) {
        for (GraphQLError error : getErrors()) {
            if (error.getPath() != null && format(error.getPath()).equals(path)) {
                return this;
            }
        }
        throw fail("expected an error at " + path);
    }

    /**
     * @param key   the key of the extensions of an error
     * @param value the expected value
     * @return this response, when an error has the extension
     * @throws AssertionError otherwise
     */
    @NonNull
    public GraphQLTestResponse assertErrorExtension(@NonNull String key, @Nullable Object value) {
        for (GraphQLError error : getErrors()) {
            Map<String, Object> extensions = error.getExtensions();
            if (extensions != null && extensions.containsKey(key) && Objects.equals(extensions.get(key), value)) {
                return this;
            }
        }
        throw fail("expected an error with the extension " + key + " = " + value);
    }

    /**
     * @param key   the key of the extensions of the response
     * @param value the expected value
     * @return this response
     * @throws AssertionError when the response has no such extension
     */
    @NonNull
    public GraphQLTestResponse assertExtension(@NonNull String key, @Nullable Object value) {
        Map<Object, Object> extensions = getExtensions();
        if (extensions == null || !extensions.containsKey(key) || !Objects.equals(extensions.get(key), value)) {
            throw fail("expected the extension " + key + " = " + value);
        }
        return this;
    }

    @Override
    public String toString() {
        return "data=" + getData() + ", errors=" + describeErrors() + ", extensions=" + getExtensions();
    }

    private List<String> describeErrors() {
        List<String> described = new ArrayList<>();
        for (GraphQLError error : getErrors()) {
            described.add(error.getMessage() + " (path=" + error.getPath() + ", extensions=" + error.getExtensions() + ")");
        }
        return described;
    }

    private AssertionError fail(String message) {
        return new AssertionError(message + ". The response: " + this);
    }

    private static String format(List<Object> path) {
        StringBuilder text = new StringBuilder();
        for (Object segment : path) {
            if (segment instanceof Integer) {
                text.append('[').append(segment).append(']');
            } else {
                if (text.length() > 0) {
                    text.append('.');
                }
                text.append(segment);
            }
        }
        return text.toString();
    }

    private Object resolve(String path) {
        Object current = getData();
        if (current == null) {
            return MISSING;
        }
        for (String part : path.split("\\.", -1)) {
            int bracket = part.indexOf('[');
            String name = bracket < 0 ? part : part.substring(0, bracket);
            if (!name.isEmpty()) {
                if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) {
                    return MISSING;
                }
                current = map.get(name);
            }
            while (bracket >= 0) {
                int close = part.indexOf(']', bracket);
                if (close < 0) {
                    throw new IllegalArgumentException("The path \"" + path + "\" has an unclosed [");
                }
                int index = Integer.parseInt(part.substring(bracket + 1, close));
                if (!(current instanceof List<?> list) || index < 0 || index >= list.size()) {
                    return MISSING;
                }
                current = list.get(index);
                bracket = part.indexOf('[', close);
            }
        }
        return current;
    }

}
