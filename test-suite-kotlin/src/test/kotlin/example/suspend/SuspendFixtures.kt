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
package example.suspend

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

// The environment of the fixtures of the Kotlin suspend resolvers (#185): suspend functions as query, mutation and type
// resolver methods, over HTTP.
const val SUSPEND_RESOLVERS = "suspend-resolvers"

const val SUSPEND_REQUEST_HEADER = "X-Suspend-Request"

// The element of the request, as a tracing or logging integration adds one.
data class SuspendRequestElement(val value: String) : PropagatedContextElement

@Factory
class SuspendFixtures {

    @Bean
    @Singleton
    @Requires(env = [SUSPEND_RESOLVERS])
    fun suspendSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Shelf { id: ID! title: String! requestOf: String! sellers: Int! label: String! }\n" +
                    "extend type Query {\n" +
                    "  shelf(id: ID!): Shelf\n" +
                    "  shelfTitles(prefix: String!): [String!]!\n" +
                    "  contextInCoroutine: String!\n" +
                    "  contextAfterIo: String!\n" +
                    "  threadAfterIo: String!\n" +
                    "  immediate: String!\n" +
                    "  failing: String!\n" +
                    "  cancelled: String!\n" +
                    "  cancelledWithExecution: String!\n" +
                    "  held: String!\n" +
                    "}\n" +
                    "extend type Mutation { renameShelf(id: ID!, title: String!): Shelf! }\n"
            )
        )
    }
}

// Puts the value of a header in the propagated context of the request.
@ServerFilter(Filter.MATCH_ALL_PATTERN)
@Requires(env = [SUSPEND_RESOLVERS])
class SuspendRequestFilter {

    @RequestFilter
    fun install(request: io.micronaut.http.HttpRequest<*>, context: MutablePropagatedContext) {
        request.headers.get(SUSPEND_REQUEST_HEADER)?.let { context.add(SuspendRequestElement(it)) }
    }
}

@GraphQLType
data class Shelf(val id: String, val title: String)

private fun currentRequest(): String =
    PropagatedContext.getOrEmpty().find(SuspendRequestElement::class.java).map { it.value }.orElse("none")

// What the coroutine of the field that is cancelled reports: the test reads it.
class CoroutineProbe {
    val started = CountDownLatch(1)
    val finished = CountDownLatch(1)
    val outcome = AtomicReference<String>()
}

object SuspendProbe {
    val started get() = direct.started
    val finished get() = direct.finished
    val outcome get() = direct.outcome
    private val direct = CoroutineProbe()

    // the field that the cancelled execution stops, and one that another execution keeps waiting for
    val execution = CoroutineProbe()
    val other = CoroutineProbe()
}

// tag::suspend-resolvers[]
@Singleton
@GraphQLRootResolver
@Requires(env = [SUSPEND_RESOLVERS])
class ShelfQueries {

    // a suspend function is a resolver method as any other: the field arguments are its parameters
    suspend fun shelf(id: String): Shelf? {
        delay(10)
        return if (id == "none") null else Shelf(id, "Shelf $id")
    }

    // a suspend function on a list field returns the list
    suspend fun shelfTitles(prefix: String): List<String> = withContext(Dispatchers.IO) {
        listOf("${prefix}1", "${prefix}2")
    }

    // the propagated context of the request is visible in the coroutine, also after it switched dispatcher
    suspend fun contextInCoroutine(): String {
        delay(5)
        return currentRequest()
    }

    suspend fun contextAfterIo(): String = withContext(Dispatchers.IO) { currentRequest() }

    suspend fun threadAfterIo(): String = withContext(Dispatchers.IO) { Thread.currentThread().name }

    // a suspend function that never suspends is a resolver as well
    suspend fun immediate(): String = "immediate"

    // an exception thrown in the coroutine becomes an error of the field
    suspend fun failing(): String {
        delay(5)
        throw IllegalStateException("the shelf is broken")
    }

    suspend fun cancelled(): String {
        SuspendProbe.started.countDown()
        try {
            delay(60_000)
        } catch (e: CancellationException) {
            SuspendProbe.outcome.set("cancelled")
            throw e
        } finally {
            SuspendProbe.finished.countDown()
        }
        return "finished"
    }

    suspend fun cancelledWithExecution(): String {
        val probe = SuspendProbe.execution
        probe.started.countDown()
        try {
            delay(60_000)
        } catch (e: CancellationException) {
            probe.outcome.set("cancelled")
            throw e
        } finally {
            probe.finished.countDown()
        }
        return "finished"
    }

    suspend fun held(): String {
        val probe = SuspendProbe.other
        probe.started.countDown()
        delay(1_000)
        probe.outcome.set("completed")
        probe.finished.countDown()
        return "held"
    }
}

@Singleton
@GraphQLRootResolver
@Requires(env = [SUSPEND_RESOLVERS])
class ShelfMutations {

    suspend fun renameShelf(id: String, title: String): Shelf {
        delay(10)
        return Shelf(id, title)
    }
}

@Singleton
@GraphQLTypeResolver(Shelf::class)
@Requires(env = [SUSPEND_RESOLVERS])
class ShelfFields {

    // a suspend type resolver method gets the parent as its first parameter
    suspend fun sellers(shelf: Shelf): Int {
        delay(5)
        return shelf.id.length
    }

    // a field that is not suspend, resolved after one that is: it runs inside the context of the request
    fun requestOf(shelf: Shelf): String = currentRequest()

    fun label(shelf: Shelf): String = "${shelf.title} (${shelf.id})"
}
// end::suspend-resolvers[]
