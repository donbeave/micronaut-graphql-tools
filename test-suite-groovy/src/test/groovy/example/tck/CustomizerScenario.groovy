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

import graphql.ExecutionInput
import groovy.transform.CompileStatic
import io.micronaut.configuration.graphql.GraphQLExecutionInputCustomizer
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// The fixtures of the TCK scenario `customizer`: an execution-input customizer that counts its calls, subscriptions and
// cancellations and records the Reactor context entry it saw, for an operation whose variables hold `track: true`; with
// `hold: true` its publisher never emits.

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'customizer')
class CustomizerCounters {
    final AtomicInteger calls = new AtomicInteger()
    final AtomicInteger subscribes = new AtomicInteger()
    final AtomicInteger cancels = new AtomicInteger()
    final AtomicInteger trackedRuns = new AtomicInteger()
    final AtomicReference<String> seen = new AtomicReference<>('none')
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'customizer')
class TrackingCustomizer implements GraphQLExecutionInputCustomizer {

    private final CustomizerCounters counters

    TrackingCustomizer(CustomizerCounters counters) {
        this.counters = counters
    }

    @Override
    Publisher<ExecutionInput> customize(ExecutionInput input, HttpRequest request, MutableHttpResponse<String> response) {
        if (input.variables.get('track') != Boolean.TRUE) {
            return Mono.just(input)
        }
        counters.calls.incrementAndGet()
        Mono<ExecutionInput> result = input.variables.get('hold') == Boolean.TRUE
                ? Mono.<ExecutionInput> never()
                : Mono.<ExecutionInput> deferContextual({ ctx ->
                    counters.seen.set((String) ctx.getOrDefault('tck-key', 'none'))
                    Mono.just(input)
                })
        return result.doOnSubscribe({ counters.subscribes.incrementAndGet() }).doOnCancel({ counters.cancels.incrementAndGet() })
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'customizer')
class CustomizerQueries {

    private final CustomizerCounters counters

    CustomizerQueries(CustomizerCounters counters) {
        this.counters = counters
    }

    int customizerCalls() {
        return counters.calls.get()
    }

    int customizerSubscribes() {
        return counters.subscribes.get()
    }

    int customizerCancels() {
        return counters.cancels.get()
    }

    String customizerSeen() {
        return counters.seen.get()
    }

    String tracked() {
        counters.trackedRuns.incrementAndGet()
        return 'ran'
    }

    int trackedRuns() {
        return counters.trackedRuns.get()
    }
}
