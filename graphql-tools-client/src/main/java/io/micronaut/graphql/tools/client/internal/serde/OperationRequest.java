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
package io.micronaut.graphql.tools.client.internal.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.OperationType;
import io.micronaut.http.HttpMethod;

/**
 * One call of a declarative client: the fixed parts of its operation and the values of its variables. The variables are two
 * parallel arrays, the shared names and the values of this call, so no map is built.
 *
 * @param document       the assembled document
 * @param operationName  the operation name, or {@code null} for an anonymous operation
 * @param operationType  the kind of the operation
 * @param httpMethod     {@link HttpMethod#POST}, or {@link HttpMethod#GET} for a query
 * @param variableNames  the names of the variables, shared by every call of the operation and never changed
 * @param variableTypes  the declared type of each variable, shared and never changed
 * @param variableValues the value of each variable in this call; it is not copied
 * @param responseType   the type that the response is decoded as, which {@link GraphQLCodec#responseType} made, shared and never changed
 */
@Internal
public record OperationRequest(
        String document,
        @Nullable String operationName,
        OperationType operationType,
        HttpMethod httpMethod,
        String[] variableNames,
        Argument<?>[] variableTypes,
        Object[] variableValues,
        Argument<?> responseType) {
}
