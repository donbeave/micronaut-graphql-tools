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
import graphql.Scalars
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.internal.GraphQLFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Shared

/**
 * The scenario matrix of the library's bean selection: what the application declares (rows) against what the
 * unqualified injection point of GraphQLSchema, GraphQL and GraphQLInvocation resolves to, and which beans of the type
 * exist. Every cell is observed on a real context. The current selection is asserted against the checked-in
 * expectation; setting the SELECTION_VARIANT environment variable to another name records the observed matrix to
 * build/bean-selection-matrix-VARIANT.txt without asserting, so that a candidate selection can be compared row by row.
 */
class BeanSelectionMatrixSpec extends AbstractTest {

    static final String SCHEMA = '''
schema { query: Query }
type Query { hello: String }
'''

    static final List<String> KINDS = [
            'none', 'plain', 'primary', 'named', 'replacesLibrary', 'replacedPlain', 'eachProperty',
            'eachPropertyPrimary', 'eachBean', 'requiresOn', 'requiresOff', 'primaryDefault',
            'named,plain', 'named,primary', 'named,requiresOn', 'plain,primary', 'named,eachProperty'
    ]
    static final List<String> TARGETS = ['schema', 'graphql', 'invocation']

    @Shared
    List<String> lines = []

    void "record the matrix"() {
        when:
        for (String kind : KINDS) {
            for (String target : TARGETS) {
                if (kind == 'primaryDefault' && target != 'invocation') {
                    continue
                }
                lines << (kind + ' | ' + target + ' | ' + observe(kind, target))
            }
        }
        def variant = (System.getenv('SELECTION_VARIANT') ?: 'today')
        def out = new File('build/bean-selection-matrix-' + variant + '.txt')
        out.parentFile.mkdirs()
        out.text = lines.join('\n') + '\n'

        then:
        variant != 'today' || lines == getClass().getResource('bean-selection-matrix-today.txt').readLines()
    }

    String observe(String kind, String target) {
        long started = System.nanoTime()
        Map config = ['spike.kind': kind, 'spike.target': target]
        if (kind.contains('requiresOn')) {
            config['spike.flag'] = 'on'
        }
        if (kind.contains('eachProperty') || kind.contains('eachBean')) {
            config['spike.items.a.v'] = '1'
            config['spike.items.b.v'] = '1'
        }
        try {
            startContext(SCHEMA, 'BeanSelectionMatrixSpec', config)
            String resolved = resolve(target)
            String all = all(target)
            return resolved + ' | all=' + all
        } catch (Throwable e) {
            Throwable root = e
            while (root.cause != null) {
                root = root.cause
            }
            return 'STARTUP-ERROR ' + root.getClass().simpleName + ' ' + root.message?.take(160)?.replace('\n', ' ')
        } finally {
            applicationContext?.close()
            applicationContext = null
        }
    }

    String resolve(String target) {
        try {
            return label(applicationContext.getBean(type(target)))
        } catch (Throwable e) {
            return 'ERROR ' + e.getClass().simpleName
        }
    }

    String all(String target) {
        applicationContext.getBeansOfType(type(target)).collect { label(it) }.sort().join(',')
    }

    static Class type(String target) {
        target == 'schema' ? GraphQLSchema : target == 'graphql' ? GraphQL : GraphQLInvocation
    }

    static String label(Object bean) {
        if (bean instanceof GraphQLSchema) {
            return bean.queryType.name
        }
        if (bean instanceof GraphQL) {
            return bean.graphQLSchema.queryType.name
        }
        bean instanceof AppInvocation ? 'inv_' + bean.label : bean.getClass().simpleName
    }

    static GraphQLSchema schema(String label) {
        GraphQLSchema.newSchema().query(GraphQLObjectType.newObject().name(label)
                .field { it.name('x').type(Scalars.GraphQLString) }.build()).build()
    }

    static GraphQL engine(String label) {
        GraphQL.newGraphQL(schema(label)).build()
    }

    static class AppInvocation implements GraphQLInvocation {
        final String label

        AppInvocation(String label) {
            this.label = label
        }

        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

    @Requires(property = 'spec.name', value = 'BeanSelectionMatrixSpec')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            'world'
        }
    }

    @Requires(property = 'spike.kind', pattern = '(.*,)?plain(,.*)?')
    @Factory
    static class PlainFactory {
        @Bean @Singleton @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema() { BeanSelectionMatrixSpec.schema('plain') }

        @Bean @Singleton @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL() { engine('plain') }

        @Bean @Singleton @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation() { new AppInvocation('plain') }
    }

    @Requires(property = 'spike.kind', pattern = '(.*,)?primary(,.*)?')
    @Factory
    static class PrimaryFactory {
        @Bean @Singleton @Primary @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema() { BeanSelectionMatrixSpec.schema('primary') }

        @Bean @Singleton @Primary @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL() { engine('primary') }

        @Bean @Singleton @Primary @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation() { new AppInvocation('primary') }
    }

    @Requires(property = 'spike.kind', pattern = '(.*,)?named(,.*)?')
    @Factory
    static class NamedFactory {
        @Bean @Singleton @Named('admin') @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema() { BeanSelectionMatrixSpec.schema('named') }

        @Bean @Singleton @Named('admin') @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL() { engine('named') }

        @Bean @Singleton @Named('admin') @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation() { new AppInvocation('named') }
    }

    @Requires(property = 'spike.kind', value = 'replacesLibrary')
    @Factory
    static class ReplacesLibraryFactory {
        @Bean @Singleton @Replaces(value = GraphQLSchema, factory = GraphQLFactory) @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema() { BeanSelectionMatrixSpec.schema('replaces') }

        @Bean @Singleton @Replaces(value = GraphQL, factory = GraphQLFactory) @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL() { engine('replaces') }
    }

    @Requires(property = 'spike.kind', value = 'replacesLibrary')
    @Requires(property = 'spike.target', value = 'invocation')
    @Singleton
    @Replaces(DefaultGraphQLInvocation)
    static class ReplacesInvocation extends AppInvocation {
        ReplacesInvocation() {
            super('replaces')
        }
    }

    @Requires(property = 'spike.kind', value = 'replacedPlain')
    @Factory
    static class ReplacedFirstFactory {
        @Bean @Singleton @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema() { BeanSelectionMatrixSpec.schema('first') }

        @Bean @Singleton @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL() { engine('first') }

        @Bean @Singleton @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation() { new AppInvocation('first') }
    }

    @Requires(property = 'spike.kind', value = 'replacedPlain')
    @Factory
    static class ReplacedSecondFactory {
        @Bean @Singleton @Named('second') @Replaces(value = GraphQLSchema, factory = ReplacedFirstFactory) @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema() { BeanSelectionMatrixSpec.schema('second') }

        @Bean @Singleton @Named('second') @Replaces(value = GraphQL, factory = ReplacedFirstFactory) @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL() { engine('second') }

        @Bean @Singleton @Named('second') @Replaces(value = GraphQLInvocation, factory = ReplacedFirstFactory) @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation() { new AppInvocation('second') }
    }

    @Requires(property = 'spike.kind', pattern = '(.*,)?eachProperty(,.*)?')
    @Factory
    static class EachPropertyFactory {
        @Bean @EachProperty('spike.items') @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema(@Parameter String name) { BeanSelectionMatrixSpec.schema('each_' + name) }

        @Bean @EachProperty('spike.items') @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL(@Parameter String name) { engine('each_' + name) }

        @Bean @EachProperty('spike.items') @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation(@Parameter String name) { new AppInvocation('each_' + name) }
    }

    @Requires(property = 'spike.kind', value = 'eachPropertyPrimary')
    @Factory
    static class EachPropertyPrimaryFactory {
        @Bean @EachProperty(value = 'spike.items', primary = 'a') @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema(@Parameter String name) { BeanSelectionMatrixSpec.schema('eachp_' + name) }

        @Bean @EachProperty(value = 'spike.items', primary = 'a') @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL(@Parameter String name) { engine('eachp_' + name) }

        @Bean @EachProperty(value = 'spike.items', primary = 'a') @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation(@Parameter String name) { new AppInvocation('eachp_' + name) }
    }

    @Requires(property = 'spike.kind', value = 'eachBean')
    @EachProperty('spike.items')
    static class Item {
        final String name

        Item(@Parameter String name) {
            this.name = name
        }
    }

    @Requires(property = 'spike.kind', value = 'eachBean')
    @Factory
    static class EachBeanFactory {
        @Bean @EachBean(Item) @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema(Item item) { BeanSelectionMatrixSpec.schema('eachb_' + item.name) }

        @Bean @EachBean(Item) @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL(Item item) { engine('eachb_' + item.name) }

        @Bean @EachBean(Item) @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation(Item item) { new AppInvocation('eachb_' + item.name) }
    }

    @Requires(property = 'spike.kind', pattern = '(.*,)?requires(On|Off)(,.*)?')
    @Requires(property = 'spike.flag', value = 'on')
    @Factory
    static class FlagFactory {
        @Bean @Singleton @Requires(property = 'spike.target', value = 'schema')
        GraphQLSchema schema() { BeanSelectionMatrixSpec.schema('flagged') }

        @Bean @Singleton @Requires(property = 'spike.target', value = 'graphql')
        GraphQL graphQL() { engine('flagged') }

        @Bean @Singleton @Requires(property = 'spike.target', value = 'invocation')
        GraphQLInvocation invocation() { new AppInvocation('flagged') }
    }

    @Requires(property = 'spike.kind', value = 'primaryDefault')
    @Factory
    static class PrimaryDefaultFactory {
        @Bean @Primary @Singleton @Requires(property = 'spike.target', value = 'invocation')
        DefaultGraphQLInvocation invocation() { new DefaultGraphQLInvocation(null, null, null) }
    }

}
