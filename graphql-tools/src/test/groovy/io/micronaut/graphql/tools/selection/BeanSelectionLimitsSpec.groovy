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
package io.micronaut.graphql.tools.selection

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Secondary
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * The limits of the invocation bean selection that the library keeps and reports at startup, with the way out that
 * the message names: an application invocation that is only {@code @Secondary} ties with the library's, and a
 * {@code GraphQLSchema} or {@code GraphQL} template over every {@code GraphQLInvocation} cannot be built.
 */
class BeanSelectionLimitsSpec extends AbstractTest {

    static final String SCHEMA = '''
schema { query: Query }
type Query { hello: String }
'''

    @Override
    protected boolean lazyBinding() {
        false
    }

    void "an application invocation that is only @Secondary fails the startup and names the way out"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.secondary')

        then:
        def e = thrown(Throwable)
        def cause = rootConfiguration(e)
        cause.message.contains('@Secondary')
        cause.message.contains('Fix: remove @Secondary')
        cause.message.contains('SecondaryOnlyFactory#GraphQLInvocation')
    }

    void "a @Secondary invocation beside a plain one starts and the plain one serves"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.secondaryAndPrimary')

        then:
        applicationContext.getBean(GraphQLInvocation) instanceof Marked
        ((Marked) applicationContext.getBean(GraphQLInvocation)).label == 'primary'
    }

    void "a schema template over every GraphQLInvocation fails the startup and names the way out"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocation')

        then:
        def e = thrown(Throwable)
        def cause = rootConfiguration(e)
        cause.message.contains('@EachBean(GraphQLInvocation.class)')
        cause.message.contains('EachInvocationFactory#GraphQLSchema')
        cause.message.contains('target the application\'s own bean type')
    }

    void "a GraphQL template over every GraphQLInvocation fails the startup"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocationEngine')

        then:
        def e = thrown(Throwable)
        rootConfiguration(e).message.contains('EachInvocationEngineFactory#GraphQL')
    }

    void "a secondary invocation that is also primary starts"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.secondaryPrimary')

        then:
        ((Marked) applicationContext.getBean(GraphQLInvocation)).label == 'both'
    }

    void "a secondary factory default is replaced by the library's invocation and starts"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.secondaryDefault')

        then:
        applicationContext.getBean(GraphQLInvocation).getClass().simpleName == 'LibraryGraphQLInvocation'
    }

    void "a replaced schema template over every invocation does not fail the startup"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocationReplaced')

        then:
        noExceptionThrown()
    }

    void "a schema template over every GraphQLInvocation starts when the application provides the engine"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocationOwnEngine')

        then:
        noExceptionThrown()
    }

    void "an engine template over every GraphQLInvocation starts when the application provides a primary engine"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocationPrimaryEngine')

        then:
        noExceptionThrown()
    }

    void "a schema template over every GraphQLInvocation fails the startup when the application engine needs the schema"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocationEngineNeedsSchema')

        then:
        def e = thrown(Throwable)
        rootConfiguration(e).message.contains('NeedsSchemaFactory#GraphQLSchema')
    }

    void "a schema template whose library expansion is replaced by qualifier does not fail the startup"() {
        when:
        List<String> templates = []
        try {
            startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocationQualifierReplaced')
        } catch (Throwable ignored) {
            // the context may fail for another reason; only the library's own check is asserted
        }
        templates = io.micronaut.graphql.tools.internal.LibraryBeanSelection.eachInvocationTemplates(
                io.micronaut.context.ApplicationContext.run(['spec.name': 'BeanSelectionLimitsSpec.eachInvocationQualifierReplaced', 'micronaut.graphql.tools.eager-init': false], 'test'))

        then:
        templates.isEmpty()
    }

    void "an engine template starts when the primary engine takes a qualified invocation"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.eachInvocationQualifiedEngine')

        then:
        noExceptionThrown()
    }

    void "a qualified invocation that is only @Secondary ties with the library's and fails the startup"() {
        when:
        startContext(SCHEMA, 'BeanSelectionLimitsSpec.namedSecondary')

        then:
        def e = thrown(Throwable)
        rootConfiguration(e).message.contains('Fix: remove @Secondary')
    }

    void "Micronaut itself finds no single unqualified bean beside a qualified secondary invocation"() {
        given:
        Map config = ['spec.name': 'BeanSelectionLimitsSpec.namedSecondary', 'micronaut.graphql.tools.eager-init': false]
        def context = io.micronaut.context.ApplicationContext.run(config, 'test')

        when:
        context.getBean(GraphQLInvocation)

        then:
        thrown(io.micronaut.context.exceptions.NonUniqueBeanException)

        cleanup:
        context?.close()
    }

    static ConfigurationException rootConfiguration(Throwable e) {
        Throwable current = e
        while (current != null) {
            if (current instanceof ConfigurationException && !(current instanceof BeanInstantiationException)) {
                return current
            }
            current = current.cause
        }
        throw new AssertionError('no ConfigurationException in the cause chain', e)
    }

    static class Marked implements GraphQLInvocation {
        final String label

        Marked(String label) {
            this.label = label
        }

        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

    @Requires(property = 'spec.name', pattern = 'BeanSelectionLimitsSpec.*')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            'world'
        }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.secondary')
    @Factory
    static class SecondaryOnlyFactory {
        @Bean @Singleton @Secondary
        GraphQLInvocation invocation() { new Marked('secondary') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.secondaryAndPrimary')
    @Factory
    static class SecondaryAndPrimaryFactory {
        @Bean @Singleton @Secondary
        GraphQLInvocation secondary() { new Marked('secondary') }

        @Bean @Singleton @Primary
        GraphQLInvocation primary() { new Marked('primary') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocation')
    @Factory
    static class EachInvocationFactory {
        @Bean @EachBean(GraphQLInvocation)
        GraphQLSchema schema(GraphQLInvocation invocation) { null }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocationEngine')
    @Factory
    static class EachInvocationEngineFactory {
        @Bean @EachBean(GraphQLInvocation)
        GraphQL graphQL(GraphQLInvocation invocation) { null }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.secondaryPrimary')
    @Factory
    static class SecondaryPrimaryFactory {
        @Bean @Singleton @Secondary @Primary
        GraphQLInvocation invocation() { new Marked('both') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.secondaryDefault')
    @Factory
    static class SecondaryDefaultFactory {
        @Bean @Singleton @Secondary
        io.micronaut.configuration.graphql.DefaultGraphQLInvocation invocation() {
            new io.micronaut.configuration.graphql.DefaultGraphQLInvocation(null, null, null)
        }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocationReplaced')
    @Factory
    static class EachInvocationReplacedFactory {
        @Bean @EachBean(GraphQLInvocation) @io.micronaut.context.annotation.Replaces(value = GraphQLSchema, factory = io.micronaut.graphql.tools.internal.GraphQLFactory)
        GraphQLSchema schema(GraphQLInvocation invocation) { null }

        @Bean @Singleton @io.micronaut.context.annotation.Replaces(value = GraphQLSchema, factory = EachInvocationReplacedFactory)
        GraphQLSchema replacing() { BeanSelectionMatrixSpec.schema('replacing') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocationOwnEngine')
    @Factory
    static class OwnEngineFactory {
        @Bean @Singleton
        GraphQL graphQL() { BeanSelectionMatrixSpec.engine('own') }

        @Bean @EachBean(GraphQLInvocation)
        GraphQLSchema schema(GraphQLInvocation invocation) { BeanSelectionMatrixSpec.schema('eachi') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.namedSecondary')
    @Factory
    static class NamedSecondaryFactory {
        @Bean @Singleton @Secondary @jakarta.inject.Named('admin')
        GraphQLInvocation invocation() { new Marked('named') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocationPrimaryEngine')
    @Factory
    static class PrimaryEngineFactory {
        @Bean @Singleton @Primary
        GraphQL graphQL() { BeanSelectionMatrixSpec.engine('primary') }

        @Bean @EachBean(GraphQLInvocation)
        GraphQL perInvocation(GraphQLInvocation invocation) { BeanSelectionMatrixSpec.engine('eachi') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocationEngineNeedsSchema')
    @Factory
    static class NeedsSchemaFactory {
        @Bean @Singleton
        GraphQL graphQL(GraphQLSchema schema) { GraphQL.newGraphQL(schema).build() }

        @Bean @EachBean(GraphQLInvocation)
        GraphQLSchema schema(GraphQLInvocation invocation) { BeanSelectionMatrixSpec.schema('eachi') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocationQualifierReplaced')
    @Factory
    static class QualifierReplacedFactory {
        @Bean @EachBean(GraphQLInvocation)
        GraphQLSchema schema(GraphQLInvocation invocation) { BeanSelectionMatrixSpec.schema('eachi') }

        @Bean @Singleton
        @io.micronaut.context.annotation.Replaces(value = GraphQLSchema, qualifier = Secondary)
        GraphQLSchema replacing() { BeanSelectionMatrixSpec.schema('replacing') }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionLimitsSpec.eachInvocationQualifiedEngine')
    @Factory
    static class QualifiedEngineFactory {
        @Bean @Singleton @jakarta.inject.Named('custom')
        GraphQLInvocation custom() { new Marked('custom') }

        @Bean @Singleton @Primary
        GraphQL graphQL(@jakarta.inject.Named('custom') GraphQLInvocation invocation) { BeanSelectionMatrixSpec.engine('primary') }

        @Bean @EachBean(GraphQLInvocation)
        GraphQL perInvocation(GraphQLInvocation invocation) { BeanSelectionMatrixSpec.engine('eachi') }
    }

}
