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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.core.annotation.Internal;

/**
 * What a call returns to its caller.
 */
@Internal
public enum Outcome {

    /** The {@code GraphQLResponse}, whatever it holds. */
    RESPONSE,

    /** The {@code data}, or the exception for the errors of the response. */
    DATA,

    /** Nothing, but the exception for the errors of the response. */
    NOTHING
}
