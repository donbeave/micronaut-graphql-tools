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

import graphql.schema.DataFetcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.execution.FieldExecutionKind;
import io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Applies the {@link GraphQLFieldFetcherDecorator} beans to a field at startup. The first decorator in order is the outermost.
 * A field that no decorator selects is returned as it came: the same instance. With no decorator bean the call
 * returns at once.
 */
@Internal
public final class GraphQLFieldFetcherDecorators {

    private final List<GraphQLFieldFetcherDecorator> innermostFirst;

    /**
     * @param decorators the decorator beans, outermost first
     */
    public GraphQLFieldFetcherDecorators(List<GraphQLFieldFetcherDecorator> decorators) {
        List<GraphQLFieldFetcherDecorator> reversed = new ArrayList<>(decorators);
        Collections.reverse(reversed);
        this.innermostFirst = reversed;
    }

    /**
     * @param field   the field
     * @param fetcher the fetcher that the library composed for it
     * @return the fetcher of the field
     */
    public DataFetcher<Object> apply(FieldDescriptor field, DataFetcher<Object> fetcher) {
        DataFetcher<Object> result = fetcher;
        for (GraphQLFieldFetcherDecorator decorator : innermostFirst) {
            if (offered(decorator, field)) {
                result = Objects.requireNonNull(decorator.decorate(field, result),
                        () -> "GraphQLFieldFetcherDecorator " + decorator.getClass().getName() + " returned null for " + field.coordinates());
            }
        }
        return result;
    }

    private static boolean offered(GraphQLFieldFetcherDecorator decorator, FieldDescriptor field) {
        if (field.kind() == FieldExecutionKind.TRIVIAL) {
            return decorator.decoratesTrivialFields();
        }
        return field.hasArguments() || decorator.decoratesFieldsWithoutArguments();
    }
}
