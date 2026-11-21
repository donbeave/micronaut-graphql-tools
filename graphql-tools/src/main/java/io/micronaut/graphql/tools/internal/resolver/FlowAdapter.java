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

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Publisher;

/**
 * Adapts the {@code Flow} that a Kotlin resolver method returns for a field of the subscription type to a {@link Publisher}.
 * The signature holds no Kotlin type: the one implementation is a bean that exists only when the Kotlin coroutine classes
 * and kotlinx-coroutines-reactive are on the classpath, so an application without them, and its native image, never links one.
 */
@Internal
public interface FlowAdapter {

    /**
     * Adapts a flow, inside the propagated context that is current on the calling thread. The flow is collected once for
     * each subscription to the publisher, and cancelling the subscription cancels the collection.
     *
     * @param flow the flow that the method returned
     * @return the publisher of the values of the flow
     */
    Publisher<Object> adapt(Object flow);

}
