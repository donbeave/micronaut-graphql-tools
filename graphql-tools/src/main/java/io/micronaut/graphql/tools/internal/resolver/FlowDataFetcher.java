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
package io.micronaut.graphql.tools.internal.resolver;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;

import java.util.concurrent.CompletionStage;

/**
 * The fetcher of a subscription field whose method returns a Kotlin {@code Flow}: it turns the flow into a {@code Publisher}
 * that the stream fetcher then wraps. Only such a field has this wrapper.
 */
@Internal
public final class FlowDataFetcher implements DataFetcher<Object> {

    private final DataFetcher<Object> delegate;
    private final FlowAdapter adapter;

    /**
     * @param delegate the fetcher that invokes the resolver method
     * @param adapter  adapts the returned flow
     */
    public FlowDataFetcher(DataFetcher<Object> delegate, FlowAdapter adapter) {
        this.delegate = delegate;
        this.adapter = adapter;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Object result = delegate.get(environment);
        if (result instanceof CompletionStage<?> stage) {
            return stage.thenApply(this::adapt);
        }
        return adapt(result);
    }

    private Object adapt(Object result) {
        return result == null ? null : adapter.adapt(result);
    }
}
