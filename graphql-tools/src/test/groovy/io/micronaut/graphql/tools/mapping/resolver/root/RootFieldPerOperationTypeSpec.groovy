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
package io.micronaut.graphql.tools.mapping.resolver.root

import graphql.ExecutionResult
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

import java.time.Duration
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class RootFieldPerOperationTypeSpec extends AbstractTest {

    static final String SPEC_NAME = "RootFieldPerOperationTypeSpec"

    @Language("GraphQL")
    static final String QUERY_AND_SUBSCRIPTION = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  ticks: Int!
}

type Subscription {
  ticks: Int!
}
"""

    @Language("GraphQL")
    static final String QUERY_AND_MUTATION = """
schema {
  query: Query
  mutation: Mutation
}

type Query {
  ticks: Int!
}

type Mutation {
  ticks: Int!
}
"""

    void "Query.ticks returning Integer and Subscription.ticks returning Publisher<Integer> each run their own method"() {
        given:
        startContext(QUERY_AND_SUBSCRIPTION, SPEC_NAME + ".subscription")

        when:
        ExecutionResult query = executeQuery('{ ticks }')
        ExecutionResult subscription = executeQuery('subscription { ticks }')
        List<ExecutionResult> events = Flux.from((Publisher<ExecutionResult>) subscription.data)
                .collectList()
                .block(Duration.ofSeconds(10))

        then:
        query.errors.isEmpty()
        query.data == [ticks: 1]
        subscription.errors.isEmpty()
        events*.data == [[ticks: 2]]
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(subscription|duplicate)')
    @GraphQLRootResolver
    static class QueryTicks {
        @GraphQLSchemaCoordinate("Query.ticks")
        Integer ticks() {
            return 1
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(subscription|duplicate)')
    @GraphQLRootResolver
    static class SubscriptionTicks {
        @GraphQLSchemaCoordinate("Subscription.ticks")
        Publisher<Integer> ticks() {
            return Flux.just(2)
        }
    }

    void "Query.ticks and Mutation.ticks each run their own method"() {
        given:
        startContext(QUERY_AND_MUTATION, SPEC_NAME + ".mutation")

        when:
        ExecutionResult query = executeQuery('{ ticks }')
        ExecutionResult mutation = executeQuery('mutation { ticks }')

        then:
        query.errors.isEmpty()
        query.data == [ticks: 1]
        mutation.errors.isEmpty()
        mutation.data == [ticks: 3]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".mutation")
    @GraphQLRootResolver
    static class MutationQueryTicks {
        @GraphQLSchemaCoordinate("Query.ticks")
        Integer ticks() {
            return 1
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".mutation")
    @GraphQLRootResolver
    static class MutationTicks {
        @GraphQLSchemaCoordinate("Mutation.ticks")
        Integer ticks() {
            return 3
        }
    }

    void "two root methods for one Subscription field fail the startup and name the operation type"() {
        given:
        startContext(QUERY_AND_SUBSCRIPTION, SPEC_NAME + ".duplicate")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.MULTIPLE_METHODS_FOUND
        e.cause.message.contains('GraphQL coordinate: Subscription.ticks')
        e.cause.message.contains(SubscriptionTicks.name)
        e.cause.message.contains(DuplicateSubscriptionTicks.name)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".duplicate")
    @GraphQLRootResolver
    static class DuplicateSubscriptionTicks {
        @GraphQLSchemaCoordinate("Subscription.ticks")
        Publisher<Integer> ticks() {
            return Flux.just(4)
        }
    }

}
