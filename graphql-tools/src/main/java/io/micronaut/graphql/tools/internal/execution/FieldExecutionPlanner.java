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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.graphql.tools.execution.FieldExecutionKind;
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict;
import io.micronaut.graphql.tools.internal.resolver.PublisherAdaptingDataFetcher;

import java.util.concurrent.Executor;

/**
 * Decides, once per field at startup, how a method field executes, and composes its fetcher. The kind comes from the
 * same {@link ReturnVerdict} that the compile-time check reads, so the startup and the build agree. A field that is not
 * {@link FieldExecutionKind#ASYNC} keeps the method fetcher it had, byte for byte: no wrapper, no thread-local read, no allocation.
 * The composition of a field, outermost first: the async boundary (async fields only), then the executor hop (a field
 * whose {@code @ExecuteOn} names another executor than the operation's), then the adapter of a publisher, then the
 * method fetcher.
 */
@Internal
public final class FieldExecutionPlanner {

    private FieldExecutionPlanner() {
    }

    /**
     * @param verdict           the return adaptation of the method
     * @param subscriptionField whether the field belongs to the subscription type
     * @return the kind of a method field
     */
    public static FieldExecutionKind kind(ReturnVerdict verdict, boolean subscriptionField) {
        if (subscriptionField) {
            return FieldExecutionKind.STREAM;
        }
        return verdict.async() ? FieldExecutionKind.ASYNC : FieldExecutionKind.SYNCHRONOUS;
    }

    /**
     * @param method            the fetcher that invokes the method
     * @param verdict           the return adaptation of the method
     * @param subscriptionField whether the field belongs to the subscription type
     * @param conversionService converts a reactive type that is not a {@code Publisher} (RxJava) to one
     * @param fieldExecutor     the executor that {@code @ExecuteOn} names when it differs from the executor of the operation, else null
     * @return the fetcher of the field
     */
    public static DataFetcher<Object> plan(DataFetcher<Object> method, ReturnVerdict verdict, boolean subscriptionField,
                                           ConversionService conversionService, @Nullable Executor fieldExecutor) {
        DataFetcher<Object> adapted = verdict.reactive() == ReturnVerdict.Reactive.NONE ? method
                : new PublisherAdaptingDataFetcher(method, verdict.reactive() == ReturnVerdict.Reactive.MULTI, conversionService);
        if (fieldExecutor != null) {
            adapted = new ExecutorHopDataFetcher(fieldExecutor, adapted);
        }
        return kind(verdict, subscriptionField) == FieldExecutionKind.ASYNC ? new AsyncBoundaryDataFetcher(adapted) : adapted;
    }
}
