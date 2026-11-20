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
package io.micronaut.graphql.tools.execution;

import graphql.ExecutionInput;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;

import java.util.Optional;

/**
 * A read-only view of one execution, which a {@link GraphQLContextContributor} receives. It is the library's own
 * per-execution state: the library creates no extra object for it.
 */
public interface GraphQLExecutionRequest {

    /**
     * @return the HTTP request, or the upgrade request of a WebSocket connection; {@code null} for a direct call
     */
    @Nullable
    HttpRequest<?> httpRequest();

    /**
     * @return the HTTP response, which a contributor may change (for example to add a header); empty when the transport
     * has none, as on a WebSocket connection
     */
    @NonNull
    Optional<MutableHttpResponse<?>> httpResponse();

    /**
     * @return how the operation arrived
     */
    @NonNull
    GraphQLTransport transport();

    /**
     * @return the attributes of the WebSocket connection; empty on every other transport
     */
    @NonNull
    ConvertibleValues<Object> connectionAttributes();

    /**
     * @return the input of the execution, as the {@code GraphQLExecutionInputCustomizer} left it
     */
    @NonNull
    ExecutionInput executionInput();

}
