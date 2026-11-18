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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

class QualifiedBeansSpec extends AbstractTest {

    static final String QUALIFIED_SCHEMA = "QualifiedBeansSpec.qualifiedSchema"
    static final String QUALIFIED_GRAPHQL = "QualifiedBeansSpec.qualifiedGraphQL"
    static final String UNQUALIFIED_GRAPHQL = "QualifiedBeansSpec.unqualifiedGraphQL"
    static final String UNQUALIFIED_SCHEMA = "QualifiedBeansSpec.unqualifiedSchema"
    static final String PRIMARY_QUALIFIED_GRAPHQL = "QualifiedBeansSpec.primaryQualifiedGraphQL"

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

    ListAppender<ILoggingEvent> appender

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        logger().addAppender(appender)
    }

    void cleanup() {
        logger().detachAppender(appender)
    }

    void "a qualified second GraphQLSchema leaves the library's binding active"() {
        given:
        startContext(SCHEMA, QUALIFIED_SCHEMA)

        when:
        def schema = applicationContext.getBean(GraphQLSchema)

        then: 'the unqualified schema is the library one, so the engine and the manifest run it'
        schema.queryType.getFieldDefinition('hello') != null
        getGraphQLBean().graphQLSchema.is(schema)
        executeQuery('{ hello }').data == [hello: 'world']
        applicationContext.getBean(BindingManifest) != null

        and: 'the qualified schema is still there, by its qualifier'
        applicationContext.getBeansOfType(GraphQLSchema).size() == 2
        applicationContext.getBean(GraphQLSchema, Qualifiers.byName('admin')).queryType.getFieldDefinition('secret') != null

        and:
        infoLogs().any {
            it.startsWith('The library creates the GraphQLSchema bean') && it.contains("with qualifier") && it.contains('admin')
        }
    }

    void "a qualified second GraphQL bean does not disable the library's engine"() {
        given:
        startContext(SCHEMA, QUALIFIED_GRAPHQL)

        when:
        def engine = getGraphQLBean()

        then: 'the unqualified engine is the library one and runs the library schema'
        applicationContext.getBeansOfType(GraphQL).size() == 2
        !engine.is(QualifiedGraphQLFactory.CREATED)
        engine.graphQLSchema.is(applicationContext.getBean(GraphQLSchema))
        executeQuery('{ hello }').data == [hello: 'world']
        applicationContext.getBean(GraphQL, Qualifiers.byName('admin')).is(QualifiedGraphQLFactory.CREATED)

        and:
        infoLogs().any { it.startsWith('The library creates the GraphQL bean') && it.contains('admin') }
    }

    void "an unqualified application GraphQL bean still replaces the library's, and the log names it"() {
        given:
        startContext(SCHEMA, UNQUALIFIED_GRAPHQL)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        getGraphQLBean().is(UnqualifiedGraphQLFactory.CREATED)
        infoLogs().any {
            it.startsWith('The library does not create a GraphQL bean') && it.contains(UnqualifiedGraphQLFactory.name)
        }
        infoLogs().any { it.startsWith('The library creates the GraphQLSchema bean') }
    }

    void "a @Primary application GraphQL bean with a qualifier also replaces the library's"() {
        given:
        startContext(SCHEMA, PRIMARY_QUALIFIED_GRAPHQL)

        expect:
        applicationContext.getBeansOfType(GraphQL).size() == 1
        getGraphQLBean().is(PrimaryQualifiedGraphQLFactory.CREATED)
        infoLogs().any {
            it.startsWith('The library does not create a GraphQL bean') && it.contains('(@Primary)')
        }
    }

    void "an unqualified application GraphQLSchema bean replaces the library's, the log names it, and the manifest message says so"() {
        given:
        startContext(null, UNQUALIFIED_SCHEMA)

        when:
        def schemas = applicationContext.getBeansOfType(GraphQLSchema)

        then:
        schemas.size() == 1
        schemas.first().is(UnqualifiedSchemaFactory.CREATED)
        infoLogs().any {
            it.startsWith('The library does not create a GraphQLSchema bean') && it.contains(UnqualifiedSchemaFactory.name)
        }

        when:
        applicationContext.getBean(BindingManifest)

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.message.contains('the library did not bind a schema')
        e.cause.message.contains(UnqualifiedSchemaFactory.name)
        !e.cause.message.contains('not bound yet')
    }

    List<String> infoLogs() {
        appender.list.findAll { it.level == Level.INFO }.collect { it.formattedMessage }
    }

    static Logger logger() {
        (Logger) LoggerFactory.getLogger('io.micronaut.graphql.tools.BeanSelectionLogger')
    }

    static GraphQLSchema otherSchema(String value) {
        return new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(ADMIN_SDL),
                RuntimeWiring.newRuntimeWiring()
                        .type('AdminQuery') { it.dataFetcher('secret', new StaticDataFetcher(value)) }
                        .build()
        )
    }

    @Requires(property = 'spec.name', pattern = 'QualifiedBeansSpec\\.(qualifiedSchema|qualifiedGraphQL|unqualifiedGraphQL|primaryQualifiedGraphQL)')
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }
    }

    @Requires(property = 'spec.name', value = QUALIFIED_SCHEMA)
    @Factory
    static class QualifiedSchemaFactory {
        @Bean
        @Singleton
        @Named('admin')
        GraphQLSchema admin() {
            return otherSchema('admin')
        }
    }

    @Requires(property = 'spec.name', value = QUALIFIED_GRAPHQL)
    @Factory
    static class QualifiedGraphQLFactory {
        static GraphQL CREATED

        @Bean
        @Singleton
        @Named('admin')
        GraphQL admin() {
            CREATED = GraphQL.newGraphQL(otherSchema('admin')).build()
            return CREATED
        }
    }

    @Requires(property = 'spec.name', value = UNQUALIFIED_GRAPHQL)
    @Factory
    static class UnqualifiedGraphQLFactory {
        static GraphQL CREATED

        @Bean
        @Singleton
        GraphQL own(GraphQLSchema schema) {
            CREATED = GraphQL.newGraphQL(schema).build()
            return CREATED
        }
    }

    @Requires(property = 'spec.name', value = PRIMARY_QUALIFIED_GRAPHQL)
    @Factory
    static class PrimaryQualifiedGraphQLFactory {
        static GraphQL CREATED

        @Bean
        @Singleton
        @Primary
        @Named('main')
        GraphQL own(GraphQLSchema schema) {
            CREATED = GraphQL.newGraphQL(schema).build()
            return CREATED
        }
    }

    @Requires(property = 'spec.name', value = UNQUALIFIED_SCHEMA)
    @Factory
    static class UnqualifiedSchemaFactory {
        static GraphQLSchema CREATED

        @Bean
        @Singleton
        GraphQLSchema own() {
            CREATED = otherSchema('own')
            return CREATED
        }
    }

}
