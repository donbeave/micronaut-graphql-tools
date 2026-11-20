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
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.scheduler.NonBlocking;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fixtures of the TCK scenarios {@code execution-thread} and {@code execution-thread-none}: a resolver says whether it
 * runs on an event loop thread, and a counting executor says how many tasks the library handed it.
 */
public final class ExecutionThreadScenario {

    static final String SCENARIOS = "execution-thread(-none)?";

    private ExecutionThreadScenario() {
    }

    /** The executor that the scenario configures: it counts the tasks it is given, so one hop per request is visible. */
    @Singleton
    @Named("tck-counting")
    @Requires(property = "tck.scenario", pattern = SCENARIOS)
    public static class CountingExecutor extends ThreadPoolExecutor {

        private final AtomicInteger tasks = new AtomicInteger();

        public CountingExecutor() {
            super(0, Integer.MAX_VALUE, 10, TimeUnit.SECONDS, new SynchronousQueue<>());
        }

        @Override
        public void execute(Runnable command) {
            tasks.incrementAndGet();
            super.execute(command);
        }

        int tasks() {
            return tasks.get();
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", pattern = SCENARIOS)
    public static class Queries {

        private final CountingExecutor executor;

        public Queries(@Named("tck-counting") CountingExecutor executor) {
            this.executor = executor;
        }

        public boolean onEventLoop() {
            return Thread.currentThread() instanceof NonBlocking;
        }

        public int executorTasks() {
            return executor.tasks();
        }
    }

}
