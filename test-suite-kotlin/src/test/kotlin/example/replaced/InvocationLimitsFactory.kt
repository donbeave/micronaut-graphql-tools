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

import graphql.Scalars
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Secondary
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import graphql.ExecutionResult
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

const val LIMITS_SECONDARY = "limits-secondary"
const val LIMITS_EACH_INVOCATION = "limits-each-invocation"
const val LIMITS_QUALIFIED_DEFAULT = "limits-qualified-default"

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Special

@Factory
class InvocationLimitsFactory {

    @Requires(env = [LIMITS_SECONDARY])
    @Bean
    @Singleton
    @Secondary
    fun secondaryInvocation(): GraphQLInvocation = GraphQLInvocation { _, _, _ -> Mono.empty() }

    @Requires(env = [LIMITS_EACH_INVOCATION])
    @Bean
    @EachBean(GraphQLInvocation::class)
    fun schemaPerInvocation(invocation: GraphQLInvocation): GraphQLSchema = GraphQLSchema.newSchema()
        .query(GraphQLObjectType.newObject().name("Query").field { it.name("x").type(Scalars.GraphQLString) }.build())
        .build()

    @Requires(env = [LIMITS_QUALIFIED_DEFAULT])
    @Bean
    @Primary
    @Singleton
    @Special
    fun qualifiedDefault(): DefaultGraphQLInvocation = DefaultGraphQLInvocation(null, null, null)

}

@Requires(env = [LIMITS_QUALIFIED_DEFAULT])
@Singleton
@Replaces(value = DefaultGraphQLInvocation::class, qualifier = Special::class)
class QualifierReplacingInvocation : GraphQLInvocation {
    override fun invoke(
        invocationData: GraphQLInvocationData,
        httpRequest: HttpRequest<*>,
        httpResponse: MutableHttpResponse<String>
    ): Publisher<ExecutionResult> = Mono.empty()
}
