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

// A thread that declares itself an event-loop thread, as Micronaut's event-loop threads do.
@CompileStatic
class AsyncEventLoopThread extends Thread implements NonBlocking {
    AsyncEventLoopThread(Runnable task) {
        super(task, 'tck-event-loop')
        setDaemon(true)
    }
}

@CompileStatic
final class AsyncThreads {
    static final String KEY = 'tck-request'
    static final ExecutorService EVENT_LOOP = Executors.newSingleThreadExecutor { Runnable task -> new AsyncEventLoopThread(task) }
    static final ExecutorService OTHER_POOL = Executors.newSingleThreadExecutor { Runnable task ->
        Thread thread = new Thread(task, 'tck-other-pool')
        thread.setDaemon(true)
        thread
    }

    private AsyncThreads() {
    }
}

// The element of the request: it sets the logging-context entry on every thread that the context is installed on.
@CompileStatic
class AsyncRequestElement implements ThreadPropagatedContextElement<String> {

    final String value

    AsyncRequestElement(String value) {
        this.value = value
    }

    @Override
    String updateThreadContext() {
        String previous = MDC.get(AsyncThreads.KEY)
        MDC.put(AsyncThreads.KEY, value)
        return previous
    }

    @Override
    void restoreThreadContext(String previous) {
        if (previous == null) {
            MDC.remove(AsyncThreads.KEY)
        } else {
            MDC.put(AsyncThreads.KEY, previous)
        }
    }
}

@CompileStatic
@ServerFilter(Filter.MATCH_ALL_PATTERN)
@Requires(property = 'tck.scenario', value = 'async-context')
class AsyncRequestContextFilter {

    @RequestFilter
    void install(MutablePropagatedContext context) {
        context.add(new AsyncRequestElement(AsyncThreads.KEY))
    }
}

@CompileStatic
@GraphQLType
class AsyncParent {
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'async-context')
class AsyncContextQueries {

    // tag::async-returns[]
    CompletionStage<AsyncParent> asyncOtherPool() {
        return completeLater(AsyncThreads.OTHER_POOL)
    }

    CompletionStage<AsyncParent> asyncNonBlocking() {
        return completeLater(AsyncThreads.EVENT_LOOP)
    }

    Mono<AsyncParent> asyncMono() {
        return Mono.fromFuture(completeLater(AsyncThreads.EVENT_LOOP))
    }
    // end::async-returns[]

    CompletionStage<AsyncParent> asyncDone() {
        return CompletableFuture.completedFuture(new AsyncParent())
    }

    private static CompletableFuture<AsyncParent> completeLater(ExecutorService executor) {
        CompletableFuture<AsyncParent> future = new CompletableFuture<AsyncParent>()
        executor.execute {
            try {
                TimeUnit.MILLISECONDS.sleep(20)
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt()
            }
            future.complete(new AsyncParent())
        }
        return future
    }
}

@CompileStatic
@GraphQLTypeResolver(AsyncParent)
@Requires(property = 'tck.scenario', value = 'async-context')
class AsyncParentFields {

    String contextElement(AsyncParent parent) {
        return PropagatedContext.getOrEmpty().find(AsyncRequestElement).map { AsyncRequestElement e -> e.value }.orElse(null)
    }

    String loggingEntry(AsyncParent parent) {
        return MDC.get(AsyncThreads.KEY)
    }

    boolean onEventLoop(AsyncParent parent) {
        return Thread.currentThread() instanceof NonBlocking
    }
}
