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
package example.flow

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.http.context.ServerRequestContext
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import reactor.core.publisher.Flux
import reactor.core.scheduler.NonBlocking
import java.time.Duration
import java.util.concurrent.CountDownLatch

// The environments of the fixtures of the Kotlin Flow subscriptions (#181): Flow fields of the subscription type over
// graphql-transport-ws, and the startup failure of a Flow on a query field.
const val FLOW_SUBSCRIPTIONS = "flow-subscriptions"
const val FLOW_ON_QUERY = "flow-on-query"

@Factory
class FlowFixtures {

    @Bean
    @Singleton
    @Requires(env = [FLOW_SUBSCRIPTIONS])
    fun flowSchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "extend schema { subscription: Subscription }\n" +
                    "type Subscription { readings(count: Int!): Reading! endless: Reading! }\n" +
                    "type Reading { number: Int! sourceNonBlocking: Boolean! onEventLoop: Boolean! requestHeader: String }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [FLOW_ON_QUERY])
    fun flowOnQuerySchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(SchemaParser().parse("extend type Query { flowOnQuery: [String!]! }\n"))
    }
}

// What the flow that the client cancels reports: the test reads it.
object FlowProbe {
    val cancelled = CountDownLatch(1)
}

@GraphQLType
class Reading(val number: Int, val sourceNonBlocking: Boolean)

// tag::flow-subscription[]
@Singleton
@GraphQLRootResolver
@Requires(env = [FLOW_SUBSCRIPTIONS])
class FlowSubscriptions {

    // a Flow is a subscription source like a Publisher; this one is fed by a timer that emits on a non-blocking thread
    fun readings(count: Int): Flow<Reading> =
        Flux.interval(Duration.ofMillis(10))
            .take(count.toLong())
            .asFlow()
            .map { number -> Reading(number.toInt(), Thread.currentThread() is NonBlocking) }

    // cancelling the subscription cancels the collection of the flow
    fun endless(): Flow<Reading> = flow {
        try {
            var number = 0
            while (true) {
                emit(Reading(number++, false))
                delay(20)
            }
        } catch (e: CancellationException) {
            FlowProbe.cancelled.countDown()
            throw e
        }
    }
}
// end::flow-subscription[]

@Singleton
@GraphQLTypeResolver(Reading::class)
@Requires(env = [FLOW_SUBSCRIPTIONS])
class ReadingFields {

    fun onEventLoop(reading: Reading): Boolean = Thread.currentThread() is NonBlocking

    fun requestHeader(reading: Reading): String? =
        ServerRequestContext.currentRequest<Any>().map { it.headers.get("X-Flow") }.orElse(null)
}

@Singleton
@GraphQLRootResolver
@Requires(env = [FLOW_ON_QUERY])
class FlowOnQuery {

    fun flowOnQuery(): Flow<String> = flowOf("a")
}
