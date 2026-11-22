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
import io.micronaut.graphql.tools.binding.internal.model.ResolverMarkers
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration
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
        query.stringValues(ResolverContracts, 'contractMethods').toList() == ['query||book/1']
        type.classValues(ResolverContracts, 'value').toList() == [Book]
        type.stringValues(ResolverContracts, 'declaredBy').toList() == [TypeResolver.name]
        type.stringValues(ResolverContracts, 'contractMethods').toList() == ["|${Book.name}|summary/1".toString()]
        stock.stringValues(ResolverContracts, 'operations').toList() == ['mutation']
        stock.stringValues(ResolverContracts, 'contractMethods').toList() == []
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

    void "a TypeResolver bean compiled without the processor fails with the contracts-not-found code at registration"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        def registry = new GraphQLResolversRegistry(applicationContext, applicationContext.getBean(GraphQLToolsConfiguration))
        registry.setTypeResolverContractReader { BeanDefinition<?> definition -> [:] }
        BeanDefinition<?> definition = applicationContext.getBeanDefinition(BookResolver)

        when:
        definition.executableMethods.each { registry.registerTypeResolverExecutableMethod(definition, it) }
        registry.verifyTypeResolverContracts()

        then:
        def e = thrown(SchemaBindingException)
        e.code() == BindingCode.TYPE_RESOLVER_CONTRACTS_NOT_FOUND
        e.message.contains('TypeResolver<T>')
        e.beanType == BookResolver
    }

    void "a root set made of marker beans compiled without the processor fails with the contracts-not-found code"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        def registry = new GraphQLResolversRegistry(applicationContext, applicationContext.getBean(GraphQLToolsConfiguration))
        registry.setMarkerReader { BeanDefinition<?> definition -> ResolverMarkers.NONE }

        expect: 'the set is not empty for the wiring, so that the failure is the one that names the fix'
        registry.hasRootResolvers()

        when:
        registry.verifyTypeResolverContracts()

        then:
        def e = thrown(SchemaBindingException)
        e.code() == BindingCode.TYPE_RESOLVER_CONTRACTS_NOT_FOUND
    }

    void "a contract method is matched by its name and its number of parameters"() {
        given:
        def markers = new ResolverMarkers(['mutation', 'query'], null, [
                new ResolverMarkers.ContractMethod(['query'], null, 'book', 1),
                new ResolverMarkers.ContractMethod(['mutation'], null, 'book', 2),
                new ResolverMarkers.ContractMethod([], Book.name, 'title', 1)])

        expect:
        markers.admits('book', 1)
        markers.admits('book', 2)
        !markers.admits('book', 3)
        !markers.admits('helper', 1)
        markers.operationsOf('book', 1) == ['query']
        markers.operationsOf('book', 2) == ['mutation']
        markers.modelsOf('title', 1) == [Book.name]
        markers.modelsOf('book', 1) == []
        ResolverMarkers.ContractMethod.decode(markers.contractMethods()[2].encode()) == markers.contractMethods()[2]
    }

    void "a method of a mixed bean is a candidate on the side of the contract that declares it"() {
        given:
        def markers = new ResolverMarkers(['query'], null, [
                new ResolverMarkers.ContractMethod(['query'], null, 'book', 1),
                new ResolverMarkers.ContractMethod([], Book.name, 'summary', 1),
                new ResolverMarkers.ContractMethod(['query'], Book.name, 'both', 1)])

        expect:
        markers.admitsRoot('book', 1)
        !markers.admitsType('book', 1)
        !markers.admitsRoot('summary', 1)
        markers.admitsType('summary', 1)
        markers.admitsRoot('both', 1)
        markers.admitsType('both', 1)
        !markers.admitsRoot('helper', 1)
        !markers.admitsType('helper', 1)
        ResolverMarkers.NONE.admitsRoot('any', 0)
        ResolverMarkers.NONE.admitsType('any', 0)
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
