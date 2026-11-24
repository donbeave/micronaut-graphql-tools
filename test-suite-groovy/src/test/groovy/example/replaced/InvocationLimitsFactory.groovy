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

import graphql.ExecutionResult
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
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Factory
class InvocationLimitsFactory {

    static final String SECONDARY = 'limits-secondary'
    static final String EACH_INVOCATION = 'limits-each-invocation'
    static final String QUALIFIED_DEFAULT = 'limits-qualified-default'

    @Requires(env = 'limits-secondary')
    @Bean
    @Singleton
    @Secondary
    GraphQLInvocation secondaryInvocation() {
        { data, request, response -> Mono.empty() } as GraphQLInvocation
    }

    @Requires(env = 'limits-each-invocation')
    @Bean
    @EachBean(GraphQLInvocation)
    GraphQLSchema schemaPerInvocation(GraphQLInvocation invocation) {
        GraphQLSchema.newSchema()
                .query(GraphQLObjectType.newObject().name('Query').field { it.name('x').type(Scalars.GraphQLString) }.build())
                .build()
    }

    @Requires(env = 'limits-qualified-default')
    @Bean
    @Primary
    @Singleton
    @Special
    DefaultGraphQLInvocation qualifiedDefault() {
        new DefaultGraphQLInvocation(null, null, null)
    }

}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Special {
}

@Requires(env = 'limits-qualified-default')
@Singleton
@Replaces(value = DefaultGraphQLInvocation, qualifier = Special)
class QualifierReplacingInvocation implements GraphQLInvocation {
    @Override
    Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
        Mono.empty()
    }
}
