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
package io.micronaut.graphql.tools.client;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A GraphQL request: a document with its operation name, variables and extensions, and the type that the {@code data} of the
 * response is decoded to. A request is immutable; build one with {@link #builder(Argument)}.
 *
 * @param <D> the type of the {@code data} of the response
 * @since 1.0.0
 */
public final class GraphQLRequest<D> {

    private static final Argument<Map<String, Object>> DYNAMIC = Argument.mapOf(String.class, Object.class);

    private final String document;
    private final String operationName;
    private final OperationType operationType;
    private final Map<String, Object> variables;
    private final Map<String, Object> extensions;
    private final Argument<D> dataType;

    private GraphQLRequest(Builder<D> builder) {
        this.document = builder.document;
        this.operationName = builder.operationName;
        this.operationType = builder.operationType;
        this.variables = copy(builder.variables);
        this.extensions = copy(builder.extensions);
        this.dataType = builder.dataType;
    }

    /**
     * Starts a request whose {@code data} is decoded to a map.
     *
     * @return the builder
     */
    public static Builder<Map<String, Object>> builder() {
        return new Builder<>(DYNAMIC);
    }

    /**
     * Starts a request whose {@code data} is decoded to a type.
     *
     * @param dataType the type of the {@code data}
     * @param <D>      the type of the {@code data}
     * @return the builder
     */
    public static <D> Builder<D> builder(Class<D> dataType) {
        return new Builder<>(Argument.of(dataType));
    }

    /**
     * Starts a request whose {@code data} is decoded to a type, which may be generic.
     *
     * @param dataType the type of the {@code data}
     * @param <D>      the type of the {@code data}
     * @return the builder
     */
    public static <D> Builder<D> builder(Argument<D> dataType) {
        return new Builder<>(dataType);
    }

    /**
     * Returns the GraphQL document.
     *
     * @return the document, never blank
     */
    public String getDocument() {
        return document;
    }

    /**
     * Returns the name of the operation to run.
     *
     * @return the operation name, or {@code null} when the document has one anonymous operation
     */
    @Nullable
    public String getOperationName() {
        return operationName;
    }

    /**
     * Returns the kind of the operation.
     *
     * @return the operation type, {@link OperationType#QUERY} unless the builder says otherwise
     */
    public OperationType getOperationType() {
        return operationType;
    }

    /**
     * Returns the variables of the operation.
     *
     * @return an unmodifiable map, empty when the request has no variables
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * Returns the extensions of the request.
     *
     * @return an unmodifiable map, empty when the request has no extensions
     */
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * Returns the type that the {@code data} of the response is decoded to.
     *
     * @return the type; a map when the builder was given none
     */
    public Argument<D> getDataType() {
        return dataType;
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /**
     * The builder of a {@link GraphQLRequest}.
     *
     * @param <D> the type of the {@code data} of the response
     * @since 1.0.0
     */
    public static final class Builder<D> {

        private final Argument<D> dataType;
        private final Map<String, Object> variables = new LinkedHashMap<>();
        private final Map<String, Object> extensions = new LinkedHashMap<>();
        private String document;
        private String operationName;
        private OperationType operationType = OperationType.QUERY;

        private Builder(Argument<D> dataType) {
            this.dataType = Objects.requireNonNull(dataType, "dataType");
        }

        /**
         * Sets the GraphQL document.
         *
         * @param document the document
         * @return this builder
         */
        public Builder<D> document(String document) {
            this.document = document;
            return this;
        }

        /**
         * Sets the name of the operation to run.
         *
         * @param operationName the operation name, or {@code null} for none
         * @return this builder
         */
        public Builder<D> operationName(@Nullable String operationName) {
            this.operationName = operationName;
            return this;
        }

        /**
         * Sets the kind of the operation. The default is {@link OperationType#QUERY}.
         *
         * @param operationType the operation type
         * @return this builder
         */
        public Builder<D> operationType(OperationType operationType) {
            this.operationType = Objects.requireNonNull(operationType, "operationType");
            return this;
        }

        /**
         * Sets one variable, replacing a variable of the same name. A {@code null} value is sent as JSON {@code null}.
         *
         * @param name  the variable name
         * @param value the value
         * @return this builder
         */
        public Builder<D> variable(String name, @Nullable Object value) {
            variables.put(Objects.requireNonNull(name, "name"), value);
            return this;
        }

        /**
         * Adds the variables of a map.
         *
         * @param variables the variables
         * @return this builder
         */
        public Builder<D> variables(Map<String, ?> variables) {
            this.variables.putAll(variables);
            return this;
        }

        /**
         * Sets one extension, replacing an extension of the same name.
         *
         * @param name  the extension name
         * @param value the value
         * @return this builder
         */
        public Builder<D> extension(String name, @Nullable Object value) {
            extensions.put(Objects.requireNonNull(name, "name"), value);
            return this;
        }

        /**
         * Adds the extensions of a map.
         *
         * @param extensions the extensions
         * @return this builder
         */
        public Builder<D> extensions(Map<String, ?> extensions) {
            this.extensions.putAll(extensions);
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         * @throws IllegalStateException when the document is missing or blank
         */
        public GraphQLRequest<D> build() {
            if (document == null || document.isBlank()) {
                throw new IllegalStateException("A GraphQL request needs a document");
            }
            return new GraphQLRequest<>(this);
        }
    }
}
