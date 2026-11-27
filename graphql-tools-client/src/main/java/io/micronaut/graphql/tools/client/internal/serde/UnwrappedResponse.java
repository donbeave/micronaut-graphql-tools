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
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.GraphQLResponse;

/**
 * The type to decode a response with the value of its single root field as the data.
 *
 * @param response the decoded response
 * @param <T>      the type of the root field
 */
@Internal
public record UnwrappedResponse<T>(GraphQLResponse<T> response) {

    static Argument<?> dataType(Argument<?> type) {
        Argument<?>[] parameters = type.getTypeParameters();
        return parameters.length == 0 ? Argument.OBJECT_ARGUMENT : parameters[0];
    }
}
