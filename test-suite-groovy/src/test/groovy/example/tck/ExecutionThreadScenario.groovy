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
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import reactor.core.scheduler.NonBlocking

import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenarios `execution-thread` and `execution-thread-none`: a resolver says whether it runs on an
// event loop thread, and a counting executor says how many tasks the library handed it.

// The executor that the scenario configures: it counts the tasks it is given, so one hop per request is visible.
@CompileStatic
@Singleton
@Named('tck-counting')
@Requires(property = 'tck.scenario', pattern = 'execution-thread(-none)?')
class CountingExecutor extends ThreadPoolExecutor {

    private final AtomicInteger tasks = new AtomicInteger()

    CountingExecutor() {
        super(0, Integer.MAX_VALUE, 10L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>())
    }

    @Override
    void execute(Runnable command) {
        tasks.incrementAndGet()
        super.execute(command)
    }

    int tasks() {
        return tasks.get()
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', pattern = 'execution-thread(-none)?')
class ExecutionThreadQueries {

    private final CountingExecutor executor

    ExecutionThreadQueries(@Named('tck-counting') CountingExecutor executor) {
        this.executor = executor
    }

    boolean onEventLoop() {
        return Thread.currentThread() instanceof NonBlocking
    }

    int executorTasks() {
        return executor.tasks()
    }
}
