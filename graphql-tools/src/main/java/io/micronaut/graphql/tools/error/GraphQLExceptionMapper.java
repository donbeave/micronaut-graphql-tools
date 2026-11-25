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
package io.micronaut.graphql.tools.error;

import graphql.GraphQLError;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/**
 * Maps an exception that a resolver threw to the GraphQL error the client receives. A mapper is a bean, found at
 * compile time, and it handles the exception type it is declared for: {@code E} and its subclasses.
 *
 * <p>Without a mapper the library hides the message of an exception: the client gets a generic error and the details
 * stay in the server log. A mapper opts one exception type in: it chooses the message and the extensions. The
 * exception is the real one: a {@link java.util.concurrent.CompletionException} or
 * {@link java.util.concurrent.ExecutionException} around it is removed first, so a mapper matches a failure of a
 * {@code CompletionStage} or a reactive type like a failure of a plain method.</p>
 *
 * <p>The mappers that match the exception are tried lowest order first ({@link io.micronaut.core.annotation.Order @Order}
 * on the class, or {@link io.micronaut.core.order.Ordered}), and the first one that returns an error wins. A mapper that returns
 * {@code null} declines, and the next one is tried. Give two mappers whose types overlap different orders: with the same order
 * their sequence is not defined. The list of mappers of an exception class is computed once and cached.</p>
 *
 * <p>Implement {@link io.micronaut.core.order.Ordered} or use {@code @Order}; lower runs first.</p>
 *
 * <pre>
 * &#64;Singleton
 * class NotFoundMapper implements GraphQLExceptionMapper&lt;NotFoundException&gt; {
 *     public GraphQLError map(NotFoundException exception, GraphQLExceptionContext context) {
 *         return context.error(exception.getMessage(), Map.of("code", "NOT_FOUND"));
 *     }
 * }
 * </pre>
 *
 * @param <E> the exception type the mapper handles
 */
public interface GraphQLExceptionMapper<E extends Throwable> {

    /**
     * Maps the exception to one error.
     *
     * @param exception the exception that the resolver threw, without a wrapping {@code CompletionException}
     * @param context   the field that failed
     * @return the error, or {@code null} to let the next mapper handle the exception
     */
    @Nullable
    GraphQLError map(@NonNull E exception, @NonNull GraphQLExceptionContext context);

    /**
     * Maps the exception to any number of errors, for an exception that carries several failures. The default is
     * the list of the one error of {@link #map}. A mapper that overrides it implements {@link #map} as the first error.
     *
     * @param exception the exception that the resolver threw, without a wrapping {@code CompletionException}
     * @param context   the field that failed
     * @return the errors, or {@code null} to let the next mapper handle the exception; an empty list answers with no error
     */
    @Nullable
    default List<GraphQLError> mapAll(@NonNull E exception, @NonNull GraphQLExceptionContext context) {
        GraphQLError error = map(exception, context);
        return error == null ? null : List.of(error);
    }

}
