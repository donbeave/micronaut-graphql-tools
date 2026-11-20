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
package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.NonBlocking;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The fixtures of the TCK scenario {@code async-context}: the request carries a propagated context element that also sets
 * a logging-context entry (the way a tracing or logging integration does), a parent field completes on another thread, and
 * its child says what the context looks like where it is resolved.
 */
public final class AsyncContextScenario {

    static final String SCENARIO = "async-context";
    static final String KEY = "tck-request";

    private AsyncContextScenario() {
    }

    /** A thread that declares itself an event-loop thread, as Micronaut's event-loop threads do. */
    static final class EventLoopThread extends Thread implements NonBlocking {
        EventLoopThread(Runnable task) {
            super(task, "tck-event-loop");
            setDaemon(true);
        }
    }

    private static final ExecutorService EVENT_LOOP = Executors.newSingleThreadExecutor(EventLoopThread::new);
    private static final ExecutorService OTHER_POOL = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tck-other-pool");
        thread.setDaemon(true);
        return thread;
    });

    /** The element of the request: it sets the logging-context entry on every thread that the context is installed on. */
    record RequestElement(String value) implements ThreadPropagatedContextElement<String> {

        @Override
        public String updateThreadContext() {
            String previous = MDC.get(KEY);
            MDC.put(KEY, value);
            return previous;
        }

        @Override
        public void restoreThreadContext(String previous) {
            if (previous == null) {
                MDC.remove(KEY);
            } else {
                MDC.put(KEY, previous);
            }
        }
    }

    @ServerFilter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class RequestContextFilter {

        @RequestFilter
        public void install(MutablePropagatedContext context) {
            context.add(new RequestElement(KEY));
        }
    }

    @GraphQLType
    public static class AsyncParent {
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Queries {

        // tag::async-returns[]
        public CompletionStage<AsyncParent> asyncOtherPool() {
            return completeLater(OTHER_POOL);
        }

        public CompletionStage<AsyncParent> asyncNonBlocking() {
            return completeLater(EVENT_LOOP);
        }

        public Mono<AsyncParent> asyncMono() {
            return Mono.fromFuture(completeLater(EVENT_LOOP));
        }
        // end::async-returns[]

        public CompletionStage<AsyncParent> asyncDone() {
            return CompletableFuture.completedFuture(new AsyncParent());
        }

        private static CompletableFuture<AsyncParent> completeLater(ExecutorService executor) {
            CompletableFuture<AsyncParent> future = new CompletableFuture<>();
            executor.execute(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                future.complete(new AsyncParent());
            });
            return future;
        }
    }

    @GraphQLTypeResolver(AsyncParent.class)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class ParentFields {

        public String contextElement(AsyncParent parent) {
            return PropagatedContext.getOrEmpty().find(RequestElement.class).map(RequestElement::value).orElse(null);
        }

        public String loggingEntry(AsyncParent parent) {
            return MDC.get(KEY);
        }

        public boolean onEventLoop(AsyncParent parent) {
            return Thread.currentThread() instanceof NonBlocking;
        }
    }
}
