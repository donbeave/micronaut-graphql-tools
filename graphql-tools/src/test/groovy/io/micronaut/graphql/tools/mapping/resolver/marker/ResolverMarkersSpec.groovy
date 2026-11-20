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
package io.micronaut.graphql.tools.mapping.resolver.marker

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry
import io.micronaut.graphql.tools.resolver.MutationResolver
import io.micronaut.graphql.tools.resolver.QueryResolver
import io.micronaut.graphql.tools.resolver.TypeResolver
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class ResolverMarkersSpec extends AbstractTest {

    static final String SPEC_NAME = "ResolverMarkersSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
  mutation: Mutation
}

type Query {
  book(id: ID!): Book
  stock: String!
}

type Mutation {
  stock: String!
}

type Book {
  id: ID!
  title: String!
  summary: String!
}
"""

    void "marker contracts bind, a generic TypeResolver binds to its type argument and a mutation marker fixes the operation"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def query = executeQuery('{ book(id: "1") { id title summary } stock }')
        def mutation = executeQuery('mutation { stock }')

        then:
        query.errors.isEmpty()
        query.data.book == [id: '1', title: 'Title 1', summary: 'Summary of Title 1']
        query.data.stock == 'queried'

        and: 'the same method name on a mutation resolver binds the mutation field only, with no ambiguity'
        mutation.errors.isEmpty()
        mutation.data.stock == 'mutated'
    }

    void "a class that implements a contract without a scope annotation is not a bean"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        expect:
        !applicationContext.containsBean(UnscopedBookQuery)
        !applicationContext.containsBean(UnscopedBookResolver)
        applicationContext.containsBean(BookQuery)
        applicationContext.containsBean(BookResolver)
    }

    void "public helpers of a contract implementation bind nothing and do not trip strict mode"() {
        when:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': true])
        def result = executeQuery('{ book(id: "2") { title summary } }')

        then:
        result.errors.isEmpty()
        result.data.book == [title: 'Title 2', summary: 'Summary of Title 2']
    }

    void "the visitor records the operation, the source type and the contract methods in the annotation metadata"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        AnnotationMetadata query = applicationContext.getBeanDefinition(BookQuery).annotationMetadata
        AnnotationMetadata type = applicationContext.getBeanDefinition(BookResolver).annotationMetadata
        AnnotationMetadata stock = applicationContext.getBeanDefinition(StockMutation).annotationMetadata

        then:
        query.stringValues(ResolverContracts, 'operations').toList() == ['query']
        query.stringValues(ResolverContracts, 'methods').toList() == ['book']
        type.classValues(ResolverContracts, 'value').toList() == [Book]
        type.stringValues(ResolverContracts, 'declaredBy').toList() == [TypeResolver.name]
        type.stringValues(ResolverContracts, 'methods').toList() == ['summary']
        stock.stringValues(ResolverContracts, 'operations').toList() == ['mutation']
        stock.stringValues(ResolverContracts, 'methods').toList() == []
    }

    void "a marker bean whose contracts the build did not record fails loudly, naming the bean"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        applicationContext.getBean(GraphQLResolversRegistry).setTypeResolverContractReader { BeanDefinition<?> definition -> [:] }

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.TYPE_RESOLVER_CONTRACTS_NOT_FOUND
        e.cause.beanType == BookResolver
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class BookQuery implements BookQueryContract {

        @Override
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }

        String helper() {
            return 'not a field'
        }

        @GraphQLIgnore
        String ignored() {
            return 'ignored'
        }
    }

    static interface BookQueryContract extends QueryResolver {
        Book book(String id)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class BookResolver implements BookResolverContract {

        @Override
        String summary(Book book) {
            return "Summary of " + book.title
        }

        String helper(Book book) {
            return 'not a field'
        }
    }

    static interface BookResolverContract extends TypeResolver<Book> {
        String summary(Book book)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class StockQuery implements QueryResolver {
        String stock() {
            return 'queried'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class StockMutation implements MutationResolver {
        String stock() {
            return 'mutated'
        }
    }

    // no scope annotation, so no bean, whatever the marker says
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class UnscopedBookQuery implements BookQueryContract {
        @Override
        Book book(String id) {
            return new Book(id: id, title: 'unscoped')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class UnscopedBookResolver implements BookResolverContract {
        @Override
        String summary(Book book) {
            return 'unscoped'
        }
    }

    @GraphQLType
    static class Book {
        String id
        String title
    }
}
