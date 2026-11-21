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

import graphql.GraphQLError
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer
import io.micronaut.graphql.tools.error.GraphQLExceptionContext
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.graphql.tools.websocket.GraphQLConnectionInterceptor
import io.micronaut.graphql.tools.websocket.GraphQLConnectionRequest
import io.micronaut.graphql.tools.websocket.GraphQLConnectionResult
import io.micronaut.http.context.ServerRequestContext
import jakarta.inject.Singleton
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import reactor.core.publisher.Flux
import reactor.core.scheduler.NonBlocking
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `websocket`: a connection interceptor that accepts a token, a subscription whose events
// come from a thread that declares itself an event-loop thread, and a counter of the streams that were cancelled.

// tag::ws-interceptor[]
/** Accepts a connection whose `connection_init` payload carries the token, and rejects every other. */
@Singleton
@Requires(property = "tck.scenario", value = "websocket")
class TokenInterceptor : GraphQLConnectionInterceptor {

    override fun intercept(request: GraphQLConnectionRequest): GraphQLConnectionResult =
        if (request.payload()["token"] == "secret") {
            GraphQLConnectionResult.accept(mapOf("user" to "alice"))
        } else {
            GraphQLConnectionResult.reject()
        }
}
// end::ws-interceptor[]

@Singleton
@Requires(property = "tck.scenario", value = "websocket")
class CancelledStreams {
    val count = AtomicInteger()
    val echoKeys = AtomicInteger()
}

/** Counts the keys that reach its batch function. */
@Singleton
@Requires(property = "tck.scenario", value = "websocket")
class EchoLoader(private val counters: CancelledStreams) : BatchLoader<Int, String> {

    override fun load(keys: List<Int>): CompletionStage<List<String>> {
        counters.echoKeys.addAndGet(keys.size)
        return CompletableFuture.completedFuture(keys.map { "e$it" })
    }
}

class TickFailure : RuntimeException("secret internal detail")

@Singleton
@Requires(property = "tck.scenario", value = "websocket")
class TickFailureMapper : GraphQLExceptionMapper<TickFailure> {

    override fun map(exception: TickFailure, context: GraphQLExceptionContext): GraphQLError =
        context.error("tick failed", mapOf("code" to "TICK_FAILED"))
}

@Singleton
@Requires(property = "tck.scenario", value = "websocket")
class StreamCustomizer : GraphQLErrorsCustomizer {

    override fun customize(errors: List<GraphQLError>): List<GraphQLError> = errors.map { error ->
        MappedError(error.message, error.path ?: emptyList(), error.locations?.firstOrNull(), (error.extensions ?: emptyMap()) + ("stream" to "customized"))
    }
}

@GraphQLType
class Tick(val number: Int, val sourceNonBlocking: Boolean)

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "websocket")
class WebSocketQueries(private val cancelled: CancelledStreams) {

    fun onEventLoop(): Boolean {
        TimeUnit.MILLISECONDS.sleep(50)
        return Thread.currentThread() is NonBlocking
    }

    fun connectionUser(environment: DataFetchingEnvironment): String? =
        ExecutionState.of(environment.graphQlContext).connectionAttributes().get("user", String::class.java).orElse(null)

    fun transport(environment: DataFetchingEnvironment): String =
        ExecutionState.of(environment.graphQlContext).transport().name

    fun cancelledStreams(): Int = cancelled.count.get()

    fun echoLoaderKeys(): Int = cancelled.echoKeys.get()
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "websocket")
class WebSocketSubscriptions(private val cancelled: CancelledStreams) {

    fun failing(): Flux<Tick> = Flux.error<Tick>(IllegalStateException("secret internal detail"))

    fun failingMapped(): Flux<Tick> = Flux.error<Tick>(TickFailure())

    fun brokenEvent(): Flux<Tick> = Flux.just(Tick(1, true))

    // tag::ws-subscription[]
    fun ticks(count: Int): Flux<Tick> =
        // the timer of Reactor emits on a thread that declares itself an event-loop thread
        Flux.interval(Duration.ofMillis(10))
            .take(count.toLong())
            .map { number -> Tick(number.toInt(), Thread.currentThread() is NonBlocking) }
    // end::ws-subscription[]

    fun endless(): Flux<Tick> =
        Flux.interval(Duration.ofMillis(20))
            .map { number -> Tick(number.toInt(), true) }
            .doOnCancel { cancelled.count.incrementAndGet() }
}

@Singleton
@GraphQLTypeResolver(Tick::class)
@Requires(property = "tck.scenario", value = "websocket")
class TickFields {

    fun onEventLoop(tick: Tick): Boolean = Thread.currentThread() is NonBlocking

    fun echo(tick: Tick, loader: DataLoader<Int, String>): CompletionStage<String> = loader.load(1)

    fun echoAgain(tick: Tick, loader: DataLoader<Int, String>): CompletionStage<String> = loader.load(1)

    fun broken(tick: Tick): String? {
        throw IllegalStateException("secret internal detail")
    }

    fun requestHeader(tick: Tick): String? =
        ServerRequestContext.currentRequest<Any>().map { it.headers.get("X-Tck") }.orElse(null)
}
