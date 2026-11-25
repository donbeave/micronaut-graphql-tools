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
package io.micronaut.graphql.tools.internal.protection;

import io.micronaut.core.annotation.Internal;

/**
 * Raised before the execution when a {@code GET} request selects a mutation. The HTTP endpoint answers it with {@code 405} and
 * {@code Allow: POST}. It carries no stack trace: it is a response, not a failure.
 */
@Internal
public final class MutationOverGetException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param operationName the name of the mutation, or null when it is anonymous
     */
    public MutationOverGetException(String operationName) {
        super(operationName == null
                ? "A mutation cannot be executed over GET. Send it with POST."
                : "The mutation '" + operationName + "' cannot be executed over GET. Send it with POST.", null, false, false);
    }
}
