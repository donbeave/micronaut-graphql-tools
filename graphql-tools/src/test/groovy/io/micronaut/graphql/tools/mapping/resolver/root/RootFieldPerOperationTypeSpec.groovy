package io.micronaut.graphql.tools.mapping.resolver.root

import graphql.ExecutionResult
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

import java.time.Duration

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
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message.contains('GraphQL coordinate: Subscription.ticks')
        e.cause.message.contains('GraphQL object type: Subscription')
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
