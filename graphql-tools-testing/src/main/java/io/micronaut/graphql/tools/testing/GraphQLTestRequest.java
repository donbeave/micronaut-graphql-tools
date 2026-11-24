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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One document to run, with its operation name, variables, context values, headers and timeout. A request is built by
 * {@link GraphQLTester#request(String)}, is not thread-safe and is meant to be executed once.
 */
public final class GraphQLTestRequest {

    private final GraphQLTester tester;
    private final String document;
    private String operationName;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final Map<String, Object> context = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Duration timeout = GraphQLTester.DEFAULT_TIMEOUT;

    GraphQLTestRequest(GraphQLTester tester, String document) {
        this.tester = tester;
        this.document = document;
    }

    /**
     * @param name the operation of the document to run, for a document with several
     * @return this request
     */
    @NonNull
    public GraphQLTestRequest operationName(@Nullable String name) {
        this.operationName = name;
        return this;
    }

    /**
     * @param name  the name of the variable
     * @param value its value: a scalar, a {@code Map} for an input object or a {@code List}
     * @return this request
     */
    @NonNull
    public GraphQLTestRequest variable(@NonNull String name, @Nullable Object value) {
        variables.put(Objects.requireNonNull(name, "name"), value);
        return this;
    }

    /**
     * @param values the variables to add
     * @return this request
     */
    @NonNull
    public GraphQLTestRequest variables(@NonNull Map<String, ?> values) {
        variables.putAll(values);
        return this;
    }

    /**
     * Puts a value in the {@code GraphQLContext} of the execution, where a resolver reads it from the
     * {@code DataFetchingEnvironment}. The value is visible to every field of the operation.
     *
     * @param key   the context key
     * @param value the value, which is not null
     * @return this request
     */
    @NonNull
    public GraphQLTestRequest context(@NonNull String key, @NonNull Object value) {
        context.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Sets a header of the synthetic HTTP request, which a context contributor or a resolver reads as it reads the header of a
     * client request.
     *
     * @param name  the header name
     * @param value the header value
     * @return this request
     */
    @NonNull
    public GraphQLTestRequest header(@NonNull String name, @NonNull String value) {
        headers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * @param limit how long {@link #execute()} waits for the operation; {@link GraphQLTester#DEFAULT_TIMEOUT} by default
     * @return this request
     */
    @NonNull
    public GraphQLTestRequest timeout(@NonNull Duration limit) {
        this.timeout = Objects.requireNonNull(limit, "limit");
        return this;
    }

    /**
     * Runs the document and waits for the response.
     *
     * @return the response, which carries the data, the errors and the extensions
     */
    @NonNull
    public GraphQLTestResponse execute() {
        return tester.run(this);
    }

    String document() {
        return document;
    }

    String operationName() {
        return operationName;
    }

    Map<String, Object> variables() {
        return variables;
    }

    Map<String, Object> context() {
        return context;
    }

    Map<String, String> headers() {
        return headers;
    }

    Duration timeout() {
        return timeout;
    }

}
