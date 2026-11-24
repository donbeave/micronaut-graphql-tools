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
package example.cancel

import graphql.schema.idl.SchemaParser
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The environment of the fixtures of the cancelled execution (#490): a field whose stage stays pending until the test
// completes it, and one that completes after a short delay.
@CompileStatic
@Factory
class CancelFixtures {

    static final String CANCEL_PENDING = 'cancel-pending'

    // the stage the held field returned, which the test inspects and completes
    static volatile CompletableFuture<String> held = new CompletableFuture<>()
    static volatile CountDownLatch heldStarted = new CountDownLatch(1)

    @Bean
    @Singleton
    @Requires(env = CancelFixtures.CANCEL_PENDING)
    GraphQLTypeDefinitionRegistryCustomizer cancelSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse('extend type Query { held: String! quick: String! }\n'))
        } as GraphQLTypeDefinitionRegistryCustomizer
    }
}

@Singleton
@CompileStatic
@GraphQLRootResolver
@Requires(env = CancelFixtures.CANCEL_PENDING)
class CancelQueries {

    CompletionStage<String> held() {
        CancelFixtures.heldStarted.countDown()
        return CancelFixtures.held
    }

    CompletionStage<String> quick() {
        return CompletableFuture.supplyAsync({ 'quick' } as java.util.function.Supplier<String>,
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS))
    }
}
