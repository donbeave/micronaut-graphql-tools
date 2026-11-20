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
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `execute-on`: two counting executors with threads of a known name, one that runs the
// operations and one that only @ExecuteOn names, and fields that say which thread they run on. A hop is one task handed to
// the executor, so the counts show which fields hopped.

// An executor that counts the tasks it is given and names its threads after the pool, without a number.
@CompileStatic
abstract class ExecuteOnCountingExecutor extends ThreadPoolExecutor {

    private final AtomicInteger tasks = new AtomicInteger()

    ExecuteOnCountingExecutor(String pool) {
        super(0, Integer.MAX_VALUE, 10L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
            @Override
            Thread newThread(Runnable task) {
                Thread thread = new Thread(task, pool + '-pool')
                thread.daemon = true
                return thread
            }
        })
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
@Singleton
@Named('tck-execute-on-request')
@Requires(property = 'tck.scenario', value = 'execute-on')
class ExecuteOnRequestExecutor extends ExecuteOnCountingExecutor {
    ExecuteOnRequestExecutor() {
        super('tck-execute-on-request')
    }
}

@CompileStatic
@Singleton
@Named('tck-execute-on-other')
@Requires(property = 'tck.scenario', value = 'execute-on')
class ExecuteOnOtherExecutor extends ExecuteOnCountingExecutor {
    ExecuteOnOtherExecutor() {
        super('tck-execute-on-other')
    }
}

@CompileStatic
@GraphQLType
class ExecuteOnBox {

    String getProperty() {
        return Thread.currentThread().name
    }

    @GraphQLField
    @ExecuteOn('tck-execute-on-other')
    String modelMethod() {
        return Thread.currentThread().name
    }
}

// tag::execute-on-model[]
@CompileStatic
@GraphQLType
@ExecuteOn('tck-execute-on-other')
class ExecuteOnCrate {

    String getProperty() {
        return Thread.currentThread().name
    }

    @GraphQLField
    String crated() {
        return Thread.currentThread().name
    }
}
// end::execute-on-model[]

@Singleton
@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'execute-on')
class ExecuteOnQueries {

    private final ExecuteOnRequestExecutor request
    private final ExecuteOnOtherExecutor other

    ExecuteOnQueries(ExecuteOnRequestExecutor request, ExecuteOnOtherExecutor other) {
        this.request = request
        this.other = other
    }

    // tag::execute-on-method[]
    String onThreadPlain() {
        return Thread.currentThread().name
    }

    @ExecuteOn('tck-execute-on-other')
    String onThreadOther() {
        return Thread.currentThread().name
    }

    @ExecuteOn('tck-execute-on-other')
    CompletionStage<String> onThreadAsync() {
        return CompletableFuture.completedFuture(Thread.currentThread().name)
    }
    // end::execute-on-method[]

    @ExecuteOn('tck-execute-on-request')
    String onThreadSame() {
        return Thread.currentThread().name
    }

    ExecuteOnBox executeOnBox() {
        return new ExecuteOnBox()
    }

    ExecuteOnCrate executeOnCrate() {
        return new ExecuteOnCrate()
    }

    int requestTasks() {
        return request.tasks()
    }

    int otherTasks() {
        return other.tasks()
    }
}

// tag::execute-on-class[]
@Singleton
@CompileStatic
@GraphQLTypeResolver(ExecuteOnBox)
@ExecuteOn('tck-execute-on-other')
@Requires(property = 'tck.scenario', value = 'execute-on')
class ExecuteOnBoxFields {

    String typeResolver(ExecuteOnBox box) {
        return Thread.currentThread().name
    }
}
// end::execute-on-class[]
