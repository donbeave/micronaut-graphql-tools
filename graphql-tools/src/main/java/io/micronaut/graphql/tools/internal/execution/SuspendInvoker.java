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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Executable;

import java.util.concurrent.CompletionStage;

/**
 * Invokes a Kotlin {@code suspend} resolver method as a {@link CompletionStage}. The signature holds no Kotlin type: the
 * one implementation is a bean that exists only when the Kotlin coroutine classes are on the classpath, so a
 * Java-only application, and its native image, never links one.
 */
@Internal
public interface SuspendInvoker {

    /**
     * Starts the coroutine on the calling thread, inside the propagated context that is current there.
     *
     * @param executable the {@code suspend} method
     * @param target     the resolver instance
     * @param arguments  the arguments of the method, with one more slot at the end, which the invoker fills with the
     *                   continuation
     * @return the stage of the result: done when the method returned without suspending
     */
    CompletionStage<Object> invoke(Executable<Object, ?> executable, Object target, Object[] arguments);

}
