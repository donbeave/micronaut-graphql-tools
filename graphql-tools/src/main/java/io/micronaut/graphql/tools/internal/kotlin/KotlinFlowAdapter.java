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
package io.micronaut.graphql.tools.internal.kotlin;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.internal.resolver.FlowAdapter;
import io.micronaut.http.bind.binders.HttpCoroutineContextFactory;
import jakarta.inject.Singleton;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.ThreadContextElement;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import org.reactivestreams.Publisher;

import java.util.List;

/**
 * Adapts a Kotlin {@code Flow} with {@code asPublisher} of kotlinx-coroutines-reactive, over the coroutine context that the
 * library gives a {@code suspend} resolver: the propagated context that is current when the method returns, and the contexts
 * of the {@code HttpCoroutineContextFactory} beans. The collection starts on the thread that subscribes and resumes on the
 * thread of its suspension point (the context holds {@code Dispatchers.Unconfined}), so the flow sees the context wherever
 * it runs. {@code asPublisher} collects the flow once for each subscriber and cancels the collection when the subscription
 * is cancelled.
 *
 * <p>The bean needs kotlinx-coroutines-reactive. Without it the bean does not exist, and binding a {@code Flow} subscription
 * fails the startup with {@code flow-without-reactive-coroutines}.</p>
 */
@Internal
@Singleton
@Requires(classes = {Flow.class, ReactiveFlowKt.class, ThreadContextElement.class})
final class KotlinFlowAdapter implements FlowAdapter {

    private final List<HttpCoroutineContextFactory<?>> contextFactories;

    KotlinFlowAdapter(List<HttpCoroutineContextFactory<?>> contextFactories) {
        this.contextFactories = contextFactories;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher<Object> adapt(Object flow) {
        return (Publisher<Object>) ReactiveFlowKt.asPublisher((Flow<?>) flow,
                CoroutineContexts.create(EmptyCoroutineContext.INSTANCE, contextFactories));
    }
}
