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
package example.replaced

import graphql.ExecutionResultImpl
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

@Factory
class ReplacedBeansFactory {

    static final String SCHEMA = 'replaced-schema'
    static final String INVOCATION = 'replaced-invocation'

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = 'replaced-schema')
    GraphQLSchema applicationSchema() {
        new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse('schema { query: Query } type Query { owner: String }'),
                RuntimeWiring.newRuntimeWiring()
                        .type('Query') { type -> type.dataFetcher('owner', new StaticDataFetcher('application schema')) }
                        .build())
    }
    // end::schema[]

    // tag::invocation[]
    @Bean
    @Singleton
    @Requires(env = 'replaced-invocation')
    GraphQLInvocation applicationInvocation() {
        { data, request, response ->
            Mono.just(ExecutionResultImpl.newExecutionResult().data([owner: 'application invocation']).build())
        } as GraphQLInvocation
    }
    // end::invocation[]

}
