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

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.bind.binders.HttpCoroutineContextFactory;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.Dispatchers;

import java.util.List;

/** Builds the coroutine context that the library gives every coroutine it starts. */
final class CoroutineContexts {

    private CoroutineContexts() {
    }

    /**
     * @param job       the job of the coroutine, or null for a context without one
     * @param factories the context factories of the application
     * @return a context that holds the current propagated context and the contexts of the factories, with
     *         {@code Dispatchers.Unconfined}, which moves no work to another thread but makes the coroutine install the
     *         propagated context on whichever thread resumes it
     */
    static CoroutineContext create(CoroutineContext job, List<HttpCoroutineContextFactory<?>> factories) {
        CoroutineContext context = EmptyCoroutineContext.INSTANCE.plus(job).plus(Dispatchers.getUnconfined());
        context = KotlinCoroutinePropagation.Companion.addPropagatedContext(context, PropagatedContext.getOrEmpty());
        for (HttpCoroutineContextFactory<?> factory : factories) {
            context = context.plus(factory.create());
        }
        return context;
    }
}
