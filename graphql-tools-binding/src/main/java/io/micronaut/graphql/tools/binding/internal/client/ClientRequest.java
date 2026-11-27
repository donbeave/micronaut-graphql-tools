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
package io.micronaut.graphql.tools.binding.internal.client;

import io.micronaut.core.annotation.Internal;

/**
 * One operation method of a client, as the annotations declare it.
 *
 * @param member     where the finding is reported: {@code binary.Name#method(parameter types)}
 * @param methodName the simple name of the method, the operation it selects when nothing else names one
 * @param schema     the {@code schema} member of the client, empty when it is not set
 * @param value      the {@code value} member of the operation: an inline document, the name of an operation of the client
 *                   directory, or empty
 * @param name       the {@code name} member of the operation, empty when it is not set
 */
@Internal
public record ClientRequest(String member, String methodName, String schema, String value, String name) {
}
