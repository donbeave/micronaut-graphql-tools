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
package io.micronaut.graphql.tools.customizer;

import graphql.GraphQLError;
import io.micronaut.core.annotation.NonNull;

import java.util.List;

/**
 * Changes the final list of errors of a response, whatever raised them: an exception of a resolver (after the
 * {@link io.micronaut.graphql.tools.error.GraphQLExceptionMapper mappers}), a validation error, a syntax error, a
 * rejected operation. It is the place for what concerns every error: a correlation id in the extensions, translated
 * messages, dropping an error.
 *
 * <p>Every bean of this type is called once for each response that has at least one error, lowest order first
 * ({@link io.micronaut.core.annotation.Order @Order} on the class, or {@link io.micronaut.core.order.Ordered}); each gets the list that the
 * previous one returned. A response without an error never calls it. A customizer that throws is logged at ERROR and
 * skipped: the list stays as it was.</p>
 */
@FunctionalInterface
public interface GraphQLErrorsCustomizer {

    /**
     * @param errors the errors of the response, never empty; do not modify the list
     * @return the errors to send; an empty list sends a response without {@code errors} and ends the chain: no later customizer is called
     */
    @NonNull
    List<GraphQLError> customize(@NonNull List<GraphQLError> errors);

}
