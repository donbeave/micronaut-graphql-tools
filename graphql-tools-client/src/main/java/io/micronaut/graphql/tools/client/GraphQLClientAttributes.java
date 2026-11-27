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

/**
 * The names of the attributes that the client sets on the HTTP request of an operation, for a filter to read.
 *
 * @since 1.0.0
 */
public final class GraphQLClientAttributes {

    /** The operation name, a {@link String}; absent for an anonymous operation. */
    public static final String OPERATION_NAME = "graphql.operation.name";

    /** The {@link OperationType} of the operation. */
    public static final String OPERATION_TYPE = "graphql.operation.type";

    private GraphQLClientAttributes() {
    }
}
