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
package io.micronaut.graphql.tools.internal.execution;

import graphql.GraphQLError;
import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * The failure of a subscription publisher, with the errors that the default error handler made of the original
 * exception. A transport sends these errors instead of its own generic one. It has no stack trace of its own.
 */
@Internal
public final class SubscriptionFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<GraphQLError> errors;

    SubscriptionFailure(Throwable cause, List<GraphQLError> errors) {
        super(cause.toString(), cause, false, false);
        this.errors = errors;
    }

    /**
     * @return the errors for the client, as the error handler made them
     */
    public List<GraphQLError> errors() {
        return errors;
    }
}
