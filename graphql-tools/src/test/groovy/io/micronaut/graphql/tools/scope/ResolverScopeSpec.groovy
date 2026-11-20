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
package io.micronaut.graphql.tools.scope

import graphql.schema.GraphQLSchema
import io.micronaut.context.annotation.Prototype
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.http.scope.RequestScope
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.util.concurrent.atomic.AtomicInteger

class ResolverScopeSpec extends AbstractTest {

    static final String SPEC_NAME = "ResolverScopeSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  single: Int!
  fresh: Int!
  book: Book!
  refreshable: Int!
  requestScoped: Int!
}

type Book {
  id: Int!
  edition: Int!
}
"""

    void "a prototype resolver is a new instance for every call and a singleton resolver is one instance"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def first = executeQuery('{ single fresh book { id edition } }')
        def second = executeQuery('{ single fresh book { id edition } }')

        then:
        first.errors.isEmpty()
        second.errors.isEmpty()
        first.data.single == second.data.single
        first.data.fresh != second.data.fresh
        first.data.book.edition != second.data.book.edition
    }

    void "a second schema build binds the same fields as the first"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        GraphQLSchema first = applicationContext.getBean(GraphQLSchema)

        when:
        GraphQLSchema second = applicationContext.createBean(GraphQLSchema)

        then:
        !first.is(second)
        fields(second) == fields(first)
        fields(first) == ['Book.edition', 'Book.id', 'Query.book', 'Query.fresh', 'Query.refreshable', 'Query.requestScoped', 'Query.single'] as Set

        when:
        def result = new graphql.GraphQL.Builder(second).build().execute('{ single fresh }')

        then:
        result.errors.isEmpty()
    }

    void "a refreshable resolver follows the refresh, a request-scoped resolver needs a request"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.errors.details': true])

        when:
        def before = executeQuery('{ refreshable }').data.refreshable
        applicationContext.publishEvent(new RefreshEvent())
        def after = executeQuery('{ refreshable }').data.refreshable
        def request = executeQuery('{ requestScoped }')

        then:
        before != after
        request.errors.size() == 1
        request.errors[0].message.contains('No request present')
    }

    private static Set<String> fields(GraphQLSchema schema) {
        ['Query', 'Book'].collectMany { typeName ->
            schema.getObjectType(typeName).fieldDefinitions.collect { typeName + '.' + it.name }
        } as Set
    }

    @Requires(property = "spec.name", value = "ResolverScopeSpec")
    @Singleton
    @GraphQLRootResolver
    static class SingletonResolver {
        private final int id = IDS.incrementAndGet()
        int single() { id }
    }

    @Requires(property = "spec.name", value = "ResolverScopeSpec")
    @Prototype
    @GraphQLRootResolver
    static class PrototypeResolver {
        private final int id = IDS.incrementAndGet()
        int fresh() { id }
    }

    @Requires(property = "spec.name", value = "ResolverScopeSpec")
    @Prototype
    @GraphQLTypeResolver(Book)
    static class BookResolver {
        private final int id = IDS.incrementAndGet()
        int edition(Book book) { id }
    }

    @GraphQLType
    static class Book {
        int id = 1
    }

    @Requires(property = "spec.name", value = "ResolverScopeSpec")
    @Singleton
    @GraphQLRootResolver
    static class BookQuery {
        Book book() { new Book() }
    }

    @Requires(property = "spec.name", value = "ResolverScopeSpec")
    @Refreshable
    @GraphQLRootResolver
    static class RefreshableResolver {
        private final int id = IDS.incrementAndGet()
        int refreshable() { id }
    }

    @Requires(property = "spec.name", value = "ResolverScopeSpec")
    @RequestScope
    @GraphQLRootResolver
    static class RequestResolver {
        private final int id = IDS.incrementAndGet()
        int requestScoped() { id }
    }

    static final AtomicInteger IDS = new AtomicInteger()

}
