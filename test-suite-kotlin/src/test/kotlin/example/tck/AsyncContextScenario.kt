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

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.ThreadPropagatedContextElement
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import org.slf4j.MDC
import reactor.core.publisher.Mono
import reactor.core.scheduler.NonBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// The fixtures of the TCK scenario `async-context`: the request carries a propagated context element that also sets a
// logging-context entry (the way a tracing or logging integration does), a parent field completes on another thread, and its
// child says what the context looks like where it is resolved.

private const val ASYNC_CONTEXT_KEY = "tck-request"

// A thread that declares itself an event-loop thread, as Micronaut's event-loop threads do.
private class EventLoopThread(task: Runnable) : Thread(task, "tck-event-loop"), NonBlocking {
    init {
        isDaemon = true
    }
}

private val eventLoop: ExecutorService = Executors.newSingleThreadExecutor { EventLoopThread(it) }
private val otherPool: ExecutorService = Executors.newSingleThreadExecutor { task ->
    Thread(task, "tck-other-pool").also { it.isDaemon = true }
}

// The element of the request: it sets the logging-context entry on every thread that the context is installed on.
data class AsyncRequestElement(val value: String) : ThreadPropagatedContextElement<String?> {

    override fun updateThreadContext(): String? {
        val previous = MDC.get(ASYNC_CONTEXT_KEY)
        MDC.put(ASYNC_CONTEXT_KEY, value)
        return previous
    }

    override fun restoreThreadContext(previous: String?) {
        if (previous == null) {
            MDC.remove(ASYNC_CONTEXT_KEY)
        } else {
            MDC.put(ASYNC_CONTEXT_KEY, previous)
        }
    }
}

@ServerFilter(Filter.MATCH_ALL_PATTERN)
@Requires(property = "tck.scenario", value = "async-context")
class AsyncRequestContextFilter {

    @RequestFilter
    fun install(context: MutablePropagatedContext) {
        context.add(AsyncRequestElement(ASYNC_CONTEXT_KEY))
    }
}

@GraphQLType
class AsyncParent

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "async-context")
class AsyncContextQueries {

    // tag::async-returns[]
    fun asyncOtherPool(): CompletionStage<AsyncParent> = completeLater(otherPool)

    fun asyncNonBlocking(): CompletionStage<AsyncParent> = completeLater(eventLoop)

    fun asyncMono(): Mono<AsyncParent> = Mono.fromFuture(completeLater(eventLoop))
    // end::async-returns[]

    fun asyncDone(): CompletionStage<AsyncParent> = CompletableFuture.completedFuture(AsyncParent())

    private fun completeLater(executor: ExecutorService): CompletableFuture<AsyncParent> {
        val future = CompletableFuture<AsyncParent>()
        executor.execute {
            try {
                TimeUnit.MILLISECONDS.sleep(20)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            future.complete(AsyncParent())
        }
        return future
    }
}

@Singleton
@GraphQLTypeResolver(AsyncParent::class)
@Requires(property = "tck.scenario", value = "async-context")
class AsyncParentFields {

    fun contextElement(parent: AsyncParent): String? =
        PropagatedContext.getOrEmpty().find(AsyncRequestElement::class.java).map { it.value }.orElse(null)

    fun loggingEntry(parent: AsyncParent): String? = MDC.get(ASYNC_CONTEXT_KEY)

    fun onEventLoop(parent: AsyncParent): Boolean = Thread.currentThread() is NonBlocking
}
