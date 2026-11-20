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
package io.micronaut.graphql.tools.customizer

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.value.PropertyCatalog
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class QualifiedBeansEdgesSpec extends AbstractTest {

    static final String REPLACED = 'QualifiedBeansEdgesSpec.replaced'
    static final String REPLACED_UNQUALIFIED = 'QualifiedBeansEdgesSpec.replacedUnqualified'
    static final String EACH_PROPERTY = 'QualifiedBeansEdgesSpec.eachProperty'
    static final String EACH_PROPERTY_PRIMARY = 'QualifiedBeansEdgesSpec.eachPropertyPrimary'
    static final String EACH_BEAN = 'QualifiedBeansEdgesSpec.eachBean'
    static final String EACH_BEAN_SCHEMA = 'QualifiedBeansEdgesSpec.eachBeanSchema'
    static final String EACH_BEAN_NONE = 'QualifiedBeansEdgesSpec.eachBeanNone'
    static final String EACH_BEAN_PRIMARY = 'QualifiedBeansEdgesSpec.eachBeanPrimary'
    static final String TEMPLATE_PRIMARY = 'QualifiedBeansEdgesSpec.templatePrimary'
    static final String REPLACED_EXPANSION = 'QualifiedBeansEdgesSpec.replacedExpansion'
    static final String REPLACED_FACTORY = 'QualifiedBeansEdgesSpec.replacedFactory'
    static final String EACH_LIST = 'QualifiedBeansEdgesSpec.eachList'
    static final String EACH_RAW = 'QualifiedBeansEdgesSpec.eachRaw'
    static final String LIBRARY = 'QualifiedBeansEdgesSpec.library'

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

    @Language("GraphQL")
    static final String ADMIN_SDL = """
schema {
  query: AdminQuery
}

type AdminQuery {
  secret: String
}
"""

    void "a GraphQL registered with singletons() replaces the library's engine"() {
        given:
        def own = GraphQL.newGraphQL(otherSchema('own')).build()
        start(LIBRARY, [:], true) { it.singletons(own) }

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        getGraphQLBean().is(own)
    }

    void "a GraphQLSchema registered with singletons() replaces the library's schema"() {
        given:
        def own = otherSchema('own')
        start(LIBRARY, [:], false) { it.singletons(own) }

        expect:
        applicationContext.getBeansOfType(GraphQLSchema).size() == 1
        applicationContext.getBean(GraphQLSchema).is(own)
    }

    void "a GraphQL runtime bean definition replaces the library's engine"() {
        given:
        def own = GraphQL.newGraphQL(otherSchema('own')).build()
        def builder = ApplicationContext.builder(['spec.name': LIBRARY, 'micronaut.graphql.tools.eager-init': false], 'test')
        applicationContext = builder.build()
        applicationContext.registerBeanDefinition(RuntimeBeanDefinition.builder(GraphQL, { own }).singleton(true).build())
        applicationContext.start()

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        getGraphQLBean().is(own)
    }

    void "a qualified runtime GraphQL does not replace the library's engine"() {
        given:
        def admin = GraphQL.newGraphQL(otherSchema('admin')).build()
        def builder = ApplicationContext.builder(['spec.name': LIBRARY, 'micronaut.graphql.tools.eager-init': false], 'test')
        applicationContext = builder.build()
        applicationContext.registerSingleton(GraphQL, admin, Qualifiers.byName('admin'))
        applicationContext.registerSingleton(TypeDefinitionRegistry, registry())
        applicationContext.start()

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 2
        !getGraphQLBean().is(admin)
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "a qualified @Replaces bean takes the unqualified bean out of the competition"() {
        given:
        start(REPLACED)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 2
        !getGraphQLBean().is(ReplacedFactory.CREATED)
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "an unqualified @Replaces bean still replaces the library's engine"() {
        given:
        start(REPLACED_UNQUALIFIED)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        getGraphQLBean().is(ReplacingUnqualifiedFactory.CREATED)
    }

    void "an @EachProperty GraphQL template is judged by the named beans it expands to"() {
        given:
        start(EACH_PROPERTY, ['qb.engines.a.x': 1, 'qb.engines.b.x': 2])

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 3
        executeQuery('{ hello }').data == [hello: 'world']
        applicationContext.getBean(GraphQL, Qualifiers.byName('a')) != null
    }

    void "an @EachProperty GraphQL template with a primary entry replaces the library's engine"() {
        given:
        start(EACH_PROPERTY_PRIMARY, ['qb.primary.a.x': 1, 'qb.primary.b.x': 2])

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 2
        getGraphQLBean().is(applicationContext.getBean(GraphQL, Qualifiers.byName('a')))
    }

    void "an @EachProperty GraphQL template without entries does not replace the library's engine"() {
        given:
        start(EACH_PROPERTY)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "an @EachBean GraphQL template over qualified beans keeps the library's engine"() {
        given:
        start(EACH_BEAN)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 3
        !getGraphQLBean().is(applicationContext.getBean(GraphQL, Qualifiers.byName('a')))
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "an @EachBean GraphQL template over the library's unqualified schema replaces the library's engine"() {
        given:
        start(EACH_BEAN_SCHEMA)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "an @EachBean GraphQL template without target beans does not replace the library's engine"() {
        given:
        start(EACH_BEAN_NONE)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "an @EachBean GraphQL template over a primary schema replaces the library's engine"() {
        given:
        start(EACH_BEAN_PRIMARY)

        expect:
        getGraphQLBean().is(applicationContext.getBean(GraphQL, Qualifiers.byName('main')))
    }

    void "a @Primary @EachProperty template replaces the library's engine even over named entries"() {
        given:
        start(TEMPLATE_PRIMARY, ['qb.tp.a.x': 1, 'qb.tp.b.x': 2])

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 2
    }

    void "a primary expansion that a named @Replaces bean replaces is not a competitor"() {
        given:
        start(REPLACED_EXPANSION, ['qb.re.a.x': 1, 'qb.re.b.x': 2])

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 3
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "replacing a whole factory does not remove its beans, so they still replace the library's engine"() {
        given:
        start(REPLACED_FACTORY)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 2
    }

    void "a list @EachProperty template is judged by its entries"() {
        given:
        start(EACH_LIST, config)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == count

        where:
        config                                  | count
        [:]                                     | 1
        ['qb.list[0].x': 1, 'qb.list[1].x': 2]  | 3
    }

    void "an @EachProperty template with the raw catalog is judged by the raw entry names"() {
        given:
        start(EACH_RAW, ['qb.raw.MainEngine.x': 1, 'qb.raw.other.x': 2])

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 2
    }

    void start(String spec, Map config = [:], boolean withRegistry = true, Closure customize = null) {
        def builder = ApplicationContext.builder(
                ['spec.name': spec, 'micronaut.graphql.tools.eager-init': false, 'micronaut.graphql.tools.manifest.record': true] << config, 'test')
        if (withRegistry) {
            builder.singletons(registry())
        }
        if (customize != null) {
            customize(builder)
        }
        applicationContext = builder.start()
    }

    static TypeDefinitionRegistry registry() {
        def registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(SCHEMA))
        registry
    }

    static GraphQLSchema otherSchema(String value) {
        new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(ADMIN_SDL),
                RuntimeWiring.newRuntimeWiring()
                        .type('AdminQuery') { it.dataFetcher('secret', new StaticDataFetcher(value)) }
                        .build())
    }

    @Requires(property = 'spec.name', pattern = 'QualifiedBeansEdgesSpec\\..*')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            'world'
        }
    }

    @Requires(property = 'spec.name', value = REPLACED)
    @Factory
    static class ReplacedFactory {
        static GraphQL CREATED

        @Bean
        @Singleton
        GraphQL own(GraphQLSchema schema) {
            CREATED = GraphQL.newGraphQL(schema).build()
            CREATED
        }
    }

    @Requires(property = 'spec.name', value = REPLACED)
    @Factory
    static class ReplacingFactory {
        @Bean
        @Singleton
        @Named('admin')
        @Replaces(value = GraphQL, factory = ReplacedFactory)
        GraphQL admin(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = REPLACED_UNQUALIFIED)
    @Factory
    static class ReplacedUnqualifiedFactory {
        @Bean
        @Singleton
        GraphQL own(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = REPLACED_UNQUALIFIED)
    @Factory
    static class ReplacingUnqualifiedFactory {
        static GraphQL CREATED

        @Bean
        @Singleton
        @Replaces(value = GraphQL, factory = ReplacedUnqualifiedFactory)
        GraphQL replacement(GraphQLSchema schema) {
            CREATED = GraphQL.newGraphQL(schema).build()
            CREATED
        }
    }

    @Requires(property = 'spec.name', value = EACH_PROPERTY)
    @Factory
    static class EachPropertyFactory {
        @EachProperty('qb.engines')
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = EACH_PROPERTY_PRIMARY)
    @Factory
    static class EachPropertyPrimaryFactory {
        @EachProperty(value = 'qb.primary', primary = 'a')
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    interface Marker {}

    @Requires(property = 'spec.name', value = EACH_BEAN)
    @Factory
    static class NamedMarkersFactory {
        @Bean
        @Singleton
        @Named('a')
        Marker a() { new Marker() {} }

        @Bean
        @Singleton
        @Named('b')
        Marker b() { new Marker() {} }

        @EachBean(Marker)
        @Singleton
        GraphQL engine(Marker marker, GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = EACH_BEAN_SCHEMA)
    @Factory
    static class PerSchemaFactory {
        @EachBean(GraphQLSchema)
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = EACH_BEAN_NONE)
    @Factory
    static class NoTargetsFactory {
        @EachBean(Marker)
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = EACH_BEAN_PRIMARY)
    @Factory
    static class PrimarySchemaFactory {
        @Bean
        @Singleton
        @Primary
        @Named('main')
        GraphQLSchema main() { otherSchema('main') }

        @EachBean(GraphQLSchema)
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = TEMPLATE_PRIMARY)
    @Factory
    static class PrimaryTemplateFactory {
        @EachProperty('qb.tp')
        @Primary
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = REPLACED_EXPANSION)
    @Factory
    static class ReplacedExpansionFactory {
        @EachProperty(value = 'qb.re', primary = 'a')
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = REPLACED_EXPANSION)
    @Factory
    static class ReplacingExpansionFactory {
        @Bean
        @Singleton
        @Named('c')
        @Replaces(value = GraphQL, named = 'a')
        GraphQL replacement(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = REPLACED_FACTORY)
    @Factory
    static class WholeReplacedFactory {
        @Bean
        @Singleton
        GraphQL own(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = REPLACED_FACTORY)
    @Factory
    @Replaces(WholeReplacedFactory)
    static class WholeReplacingFactory {
        @Bean
        @Singleton
        @Named('admin')
        GraphQL admin(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = EACH_LIST)
    @Factory
    static class EachListFactory {
        @EachProperty(value = 'qb.list', list = true)
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }

    @Requires(property = 'spec.name', value = EACH_RAW)
    @Factory
    static class EachRawFactory {
        @EachProperty(value = 'qb.raw', primary = 'MainEngine', catalog = PropertyCatalog.RAW)
        @Singleton
        GraphQL engine(GraphQLSchema schema) {
            GraphQL.newGraphQL(schema).build()
        }
    }
}
