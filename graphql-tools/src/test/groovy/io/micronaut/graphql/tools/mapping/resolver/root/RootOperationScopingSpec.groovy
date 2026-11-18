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
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

import java.time.Duration

class RootOperationScopingSpec extends AbstractTest {

    static final String SPEC_NAME = "RootOperationScopingSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}

type Query {
  status(id: ID!): String!
}

type Mutation {
  status(id: ID!): String!
}

type Subscription {
  status(id: ID!): String!
}
"""

    void "the same field name on three operation types binds three methods by their coordinates"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".scoped")

        when:
        def query = executeQuery('{ status(id: "a") }')
        def mutation = executeQuery('mutation { status(id: "b") }')
        ExecutionResult subscription = executeQuery('subscription { status(id: "c") }')
        List<ExecutionResult> events = Flux.from((Publisher<ExecutionResult>) subscription.data)
                .collectList()
                .block(Duration.ofSeconds(10))

        then:
        query.errors.isEmpty()
        query.data.status == 'query a'
        mutation.errors.isEmpty()
        mutation.data.status == 'mutation b'
        subscription.errors.isEmpty()
        events*.data == [[status: 'event c']]
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(scoped|twoCoordinates|mixed)')
    @GraphQLRootResolver
    static class StatusQuery {
        @GraphQLSchemaCoordinate("Query.status")
        String status(String id) {
            return 'query ' + id
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(scoped|twoCoordinates)')
    @GraphQLRootResolver
    static class StatusMutation {
        @GraphQLSchemaCoordinate("Mutation.status")
        String status(String id) {
            return 'mutation ' + id
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(scoped|twoCoordinates)')
    @GraphQLRootResolver
    static class StatusSubscription {
        @GraphQLSchemaCoordinate("Subscription.status")
        Publisher<String> status(String id) {
            return Flux.just('event ' + id)
        }
    }

    void "a root method without a coordinate binds the field of its name on every operation type"() {
        given:
        startContext("""
schema {
  query: Query
  mutation: Mutation
}

type Query {
  ping: String!
}

type Mutation {
  ping: String!
}
""", SPEC_NAME + ".unscoped", ['micronaut.graphql.tools.strict': false])

        when:
        def query = executeQuery('{ ping }')
        def mutation = executeQuery('mutation { ping }')

        then:
        query.data.ping == 'pong'
        mutation.data.ping == 'pong'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unscoped")
    @GraphQLRootResolver
    static class PingResolver {
        String ping() {
            return 'pong'
        }
    }

    void "a field whose name only a coordinate of another operation type matches is not bound"() {
        given:
        startContext(SCHEMA.replace('\ntype Subscription {\n  status(id: ID!): String!\n}\n', '')
                .replace('  subscription: Subscription\n', ''), SPEC_NAME + ".queryOnly")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """[field-not-bound] The method `status` not found in any root resolvers: [${QueryOnlyStatus.name}].
  GraphQL coordinate: Mutation.status (line 12, column 3)
  GraphQL object type: Mutation
  GraphQL field: status"""
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".queryOnly")
    @GraphQLRootResolver
    static class QueryOnlyStatus {
        @GraphQLSchemaCoordinate("Query.status")
        String status(String id) {
            return 'query ' + id
        }
    }

    void "two root resolver beans with a method of one field name fail the startup, whatever the bean order"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  status(id: ID!): String!
}
""", SPEC_NAME + ".twoBeans")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """[multiple-methods-found] Found multiple methods for one GraphQL field.
  GraphQL coordinate: Query.status (line 7, column 3)
  GraphQL object type: Query
  GraphQL field: status
  Methods:\u0020
  1) ${FirstStatusQuery.name} status(java.lang.String id)
  2) ${SecondStatusQuery.name} status(java.lang.String id)
  Fix: if one bean is meant to override another, annotate the overriding bean with @Replaces(<overridden bean>.class) ('@Requires(env = ...)' alone does not drop a bean); if the methods serve different operation types, annotate each with the @GraphQLSchemaCoordinate("<Operation>.<field>") of its own operation type, for this field @GraphQLSchemaCoordinate("Query.status") on one of them (two methods with the same coordinate still conflict)."""
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(twoBeans|mixed)')
    @GraphQLRootResolver
    static class FirstStatusQuery {
        String status(String id) {
            return 'first'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".twoBeans")
    @GraphQLRootResolver
    static class SecondStatusQuery {
        String status(String id) {
            return 'second'
        }
    }

    void "two methods with one root coordinate fail the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".twoCoordinates")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """[multiple-methods-found] Found multiple methods for one GraphQL field.
  GraphQL coordinate: Query.status (line 9, column 3)
  GraphQL object type: Query
  GraphQL field: status
  Methods:\u0020
  1) ${DuplicateStatusQuery.name} status(java.lang.String id)
  2) ${StatusQuery.name} status(java.lang.String id)
  Fix: if one bean is meant to override another, annotate the overriding bean with @Replaces(<overridden bean>.class) ('@Requires(env = ...)' alone does not drop a bean); if the methods serve different operation types, annotate each with the @GraphQLSchemaCoordinate("<Operation>.<field>") of its own operation type, for this field @GraphQLSchemaCoordinate("Query.status") on one of them (two methods with the same coordinate still conflict)."""
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".twoCoordinates")
    @GraphQLRootResolver
    static class DuplicateStatusQuery {
        @GraphQLSchemaCoordinate("Query.status")
        String status(String id) {
            return 'duplicate ' + id
        }
    }

    void "a coordinate method and a method without coordinate of another bean for one root field fail the startup"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  status(id: ID!): String!
}
""", SPEC_NAME + ".mixed")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """[multiple-methods-found] Found multiple methods for one GraphQL field.
  GraphQL coordinate: Query.status (line 7, column 3)
  GraphQL object type: Query
  GraphQL field: status
  Methods:\u0020
  1) ${FirstStatusQuery.name} status(java.lang.String id)
  2) ${StatusQuery.name} status(java.lang.String id)
  Fix: if one bean is meant to override another, annotate the overriding bean with @Replaces(<overridden bean>.class) ('@Requires(env = ...)' alone does not drop a bean); if the methods serve different operation types, annotate each with the @GraphQLSchemaCoordinate("<Operation>.<field>") of its own operation type, for this field @GraphQLSchemaCoordinate("Query.status") on one of them (two methods with the same coordinate still conflict)."""
    }

}
