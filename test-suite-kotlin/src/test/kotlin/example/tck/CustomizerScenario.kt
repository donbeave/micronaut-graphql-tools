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
import io.micronaut.configuration.graphql.GraphQLExecutionInputCustomizer
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.tck.TckContextElement
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

@Singleton
@Requires(property = "tck.scenario", value = "customizer")
class CustomizerCounters {
    val calls = AtomicInteger()
    val subscribes = AtomicInteger()
    val cancels = AtomicInteger()
    val trackedRuns = AtomicInteger()
    val seen = AtomicReference("none")
    val context = AtomicReference("none")
}

@Singleton
@Requires(property = "tck.scenario", value = "customizer")
class TrackingCustomizer(private val counters: CustomizerCounters) : GraphQLExecutionInputCustomizer {

    override fun customize(input: ExecutionInput, request: HttpRequest<*>?, response: MutableHttpResponse<String>?): Publisher<ExecutionInput> {
        if (input.variables["track"] != true) {
            return Mono.just(input)
        }
        counters.calls.incrementAndGet()
        val result: Mono<ExecutionInput> = if (input.variables["hold"] == true) {
            Mono.never()
        } else {
            Mono.deferContextual { context ->
                counters.seen.set(context.getOrDefault("tck-key", "none"))
                Mono.just(input)
            }
        }
        return result.doOnSubscribe { counters.subscribes.incrementAndGet() }.doOnCancel { counters.cancels.incrementAndGet() }
    }
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "customizer")
class CustomizerQueries(private val counters: CustomizerCounters) {

    fun customizerCalls(): Int = counters.calls.get()

    fun customizerSubscribes(): Int = counters.subscribes.get()

    fun customizerCancels(): Int = counters.cancels.get()

    fun customizerSeen(): String = counters.seen.get()

    fun tracked(): String {
        counters.trackedRuns.incrementAndGet()
        counters.context.set(PropagatedContext.getOrEmpty().find(TckContextElement::class.java).map { it.id }.orElse("none"))
        return "ran"
    }

    fun trackedContext(): String = counters.context.get()

    fun trackedRuns(): Int = counters.trackedRuns.get()
}
