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

import groovy.transform.CompileStatic
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `bean-scopes`: a resolver is a singleton, and the beans it uses keep their own scope.

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'bean-scopes')
class IdSequence {
    private final AtomicInteger next = new AtomicInteger()

    int next() {
        return next.incrementAndGet()
    }
}

@CompileStatic
@RequestScope
@Requires(property = 'tck.scenario', value = 'bean-scopes')
class RequestCounter {
    private final AtomicInteger count = new AtomicInteger()

    int next() {
        return count.incrementAndGet()
    }
}

@CompileStatic
@Prototype
@Requires(property = 'tck.scenario', value = 'bean-scopes')
class Ticket {
    final int id

    Ticket(IdSequence sequence) {
        this.id = sequence.next()
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'bean-scopes')
class BeanScopesQueries {

    private final AtomicInteger singletonCount = new AtomicInteger()
    private final RequestCounter requestCounter
    private final Ticket injected
    private final BeanProvider<Ticket> tickets

    BeanScopesQueries(RequestCounter requestCounter, Ticket injected, BeanProvider<Ticket> tickets) {
        this.requestCounter = requestCounter
        this.injected = injected
        this.tickets = tickets
    }

    int singletonCount() {
        return singletonCount.incrementAndGet()
    }

    int requestScopedCount() {
        return requestCounter.next()
    }

    int injectedPrototypeId() {
        return injected.id
    }

    int freshPrototypeId() {
        return tickets.get().id
    }
}
