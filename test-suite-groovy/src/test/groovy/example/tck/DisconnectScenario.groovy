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
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `disconnect`: a field that completes after a delay and a child field of its result, with
// a counter of how often each ran.

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'disconnect')
class DisconnectCounters {
    final AtomicInteger starts = new AtomicInteger()
    final AtomicInteger childRuns = new AtomicInteger()
}

@CompileStatic
@GraphQLType
class SlowParent {
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'disconnect')
class DisconnectQueries {

    private final DisconnectCounters counters

    DisconnectQueries(DisconnectCounters counters) {
        this.counters = counters
    }

    // tag::disconnect-slow-field[]
    CompletionStage<SlowParent> slowParent() {
        counters.starts.incrementAndGet()
        return CompletableFuture.supplyAsync({ new SlowParent() }, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS))
    }
    // end::disconnect-slow-field[]

    int slowStarts() {
        return counters.starts.get()
    }

    int childRuns() {
        return counters.childRuns.get()
    }
}

@CompileStatic
@Singleton
@GraphQLTypeResolver(SlowParent)
@Requires(property = 'tck.scenario', value = 'disconnect')
class SlowParentFields {

    private final DisconnectCounters counters

    SlowParentFields(DisconnectCounters counters) {
        this.counters = counters
    }

    String child(SlowParent parent) {
        counters.childRuns.incrementAndGet()
        return 'ran'
    }
}
