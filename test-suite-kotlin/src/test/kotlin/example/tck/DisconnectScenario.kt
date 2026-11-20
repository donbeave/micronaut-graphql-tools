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
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `disconnect`: a field that completes after a delay and a child field of its result, with
// a counter of how often each ran.

@Singleton
@Requires(property = "tck.scenario", value = "disconnect")
class DisconnectCounters {
    val starts = AtomicInteger()
    val childRuns = AtomicInteger()
}

@GraphQLType
class SlowParent

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "disconnect")
class DisconnectQueries(private val counters: DisconnectCounters) {

    // tag::disconnect-slow-field[]
    fun slowParent(): CompletionStage<SlowParent> {
        counters.starts.incrementAndGet()
        return CompletableFuture.supplyAsync({ SlowParent() }, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS))
    }
    // end::disconnect-slow-field[]

    fun slowStarts(): Int = counters.starts.get()

    fun childRuns(): Int = counters.childRuns.get()
}

@Singleton
@GraphQLTypeResolver(SlowParent::class)
@Requires(property = "tck.scenario", value = "disconnect")
class SlowParentFields(private val counters: DisconnectCounters) {

    fun child(parent: SlowParent): String {
        counters.childRuns.incrementAndGet()
        return "ran"
    }
}
