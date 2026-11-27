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
package io.micronaut.graphql.tools.binding.internal.client.method;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * One operation method of a client, as the rules of its parameters and its result see it. The adapter of the compiler builds it.
 *
 * @param member     where a finding is reported: {@code binary.Name#method(parameter types)}
 * @param parameters the parameters, a Kotlin continuation excluded
 */
@Internal
public record ClientMethod(String member, List<ClientParameter> parameters) {

    public ClientMethod {
        parameters = List.copyOf(parameters);
    }
}
