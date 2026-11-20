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
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `execute-on`: two counting executors with threads of a known name, one that runs the
// operations and one that only @ExecuteOn names, and fields that say which thread they run on. A hop is one task handed to
// the executor, so the counts show which fields hopped.

private const val EXECUTE_ON_REQUEST = "tck-execute-on-request"
private const val EXECUTE_ON_OTHER = "tck-execute-on-other"

// An executor that counts the tasks it is given and names its threads after the pool, without a number.
abstract class ExecuteOnCountingExecutor(pool: String) : ThreadPoolExecutor(
    0, Int.MAX_VALUE, 10, TimeUnit.SECONDS, SynchronousQueue(),
    { task -> Thread(task, "$pool-pool").also { it.isDaemon = true } },
) {

    private val tasks = AtomicInteger()

    override fun execute(command: Runnable) {
        tasks.incrementAndGet()
        super.execute(command)
    }

    fun tasks(): Int = tasks.get()
}

@Singleton
@Named(EXECUTE_ON_REQUEST)
@Requires(property = "tck.scenario", value = "execute-on")
class ExecuteOnRequestExecutor : ExecuteOnCountingExecutor(EXECUTE_ON_REQUEST)

@Singleton
@Named(EXECUTE_ON_OTHER)
@Requires(property = "tck.scenario", value = "execute-on")
class ExecuteOnOtherExecutor : ExecuteOnCountingExecutor(EXECUTE_ON_OTHER)

@GraphQLType
class ExecuteOnBox {

    val property: String
        get() = Thread.currentThread().name

    @GraphQLField
    @ExecuteOn(EXECUTE_ON_OTHER)
    fun modelMethod(): String = Thread.currentThread().name
}

// tag::execute-on-model[]
@GraphQLType
@ExecuteOn(EXECUTE_ON_OTHER)
class ExecuteOnCrate {

    val property: String
        get() = Thread.currentThread().name

    @GraphQLField
    fun crated(): String = Thread.currentThread().name
}
// end::execute-on-model[]

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "execute-on")
class ExecuteOnQueries(private val request: ExecuteOnRequestExecutor, private val other: ExecuteOnOtherExecutor) {

    // tag::execute-on-method[]
    fun onThreadPlain(): String = Thread.currentThread().name

    @ExecuteOn(EXECUTE_ON_OTHER)
    fun onThreadOther(): String = Thread.currentThread().name

    @ExecuteOn(EXECUTE_ON_OTHER)
    fun onThreadAsync(): CompletionStage<String> = CompletableFuture.completedFuture(Thread.currentThread().name)
    // end::execute-on-method[]

    @ExecuteOn(EXECUTE_ON_REQUEST)
    fun onThreadSame(): String = Thread.currentThread().name

    fun executeOnBox(): ExecuteOnBox = ExecuteOnBox()

    fun executeOnCrate(): ExecuteOnCrate = ExecuteOnCrate()

    fun requestTasks(): Int = request.tasks()

    fun otherTasks(): Int = other.tasks()
}

// tag::execute-on-class[]
@Singleton
@GraphQLTypeResolver(ExecuteOnBox::class)
@ExecuteOn(EXECUTE_ON_OTHER)
@Requires(property = "tck.scenario", value = "execute-on")
class ExecuteOnBoxFields {

    fun typeResolver(box: ExecuteOnBox): String = Thread.currentThread().name
}
// end::execute-on-class[]
