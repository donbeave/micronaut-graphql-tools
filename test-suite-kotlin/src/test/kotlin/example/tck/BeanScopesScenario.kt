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
package example.tck

import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `bean-scopes`: a resolver is a singleton, and the beans it uses keep their own scope.

@Singleton
@Requires(property = "tck.scenario", value = "bean-scopes")
open class IdSequence {
    private val next = AtomicInteger()

    fun next(): Int = next.incrementAndGet()
}

@RequestScope
@Requires(property = "tck.scenario", value = "bean-scopes")
open class RequestCounter {
    private val count = AtomicInteger()

    open fun next(): Int = count.incrementAndGet()
}

@Prototype
@Requires(property = "tck.scenario", value = "bean-scopes")
class Ticket(sequence: IdSequence) {
    val id: Int = sequence.next()
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "bean-scopes")
class BeanScopesQueries(
    private val requestCounter: RequestCounter,
    private val injected: Ticket,
    private val tickets: BeanProvider<Ticket>
) {

    private val singletonCount = AtomicInteger()

    fun singletonCount(): Int = singletonCount.incrementAndGet()

    fun requestScopedCount(): Int = requestCounter.next()

    fun injectedPrototypeId(): Int = injected.id

    fun freshPrototypeId(): Int = tickets.get().id
}
