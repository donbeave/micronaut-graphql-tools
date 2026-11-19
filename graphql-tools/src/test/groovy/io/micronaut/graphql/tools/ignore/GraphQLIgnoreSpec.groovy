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
package io.micronaut.graphql.tools.ignore

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.annotation.PreDestroy
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException

class GraphQLIgnoreSpec extends AbstractTest {

    static final String SPEC_JAVA = "IgnoreSpec"
    static final String SPEC_GROOVY = "IgnoreSpecGroovy"
    static final String SPEC_UNBOUND = "IgnoreSpecUnbound"
    static final String SPEC_IGNORED_ROOT = "IgnoreSpecIgnoredRoot"
    static final String SPEC_IGNORED_GETTER = "IgnoreSpecIgnoredGetter"
    static final String SPEC_IGNORED_COORDINATE = "IgnoreSpecIgnoredCoordinate"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  item: Item!
  level: Level
  describe(filter: Filter!): String
}

type Item {
  name: String!
  label: String!
}

enum Level {
  LOW
  HIGH
}

input Filter {
  text: String!
}
"""

    void "java: the lifecycle methods and every ignored member pass strict mode, and nothing binds the ignored ones"() {
        given:
        startContext(SCHEMA, SPEC_JAVA)

        when:
        def result = executeQuery('{ item { name label } level describe(filter: {text: "t"}) }')

        then:
        result.errors.isEmpty()
        result.data == [item: [name: 'resolver', label: 'label'], level: 'HIGH', describe: 't']
        def manifest = applicationContext.getBean(BindingManifest)
        manifest.diagnostics.isEmpty()
        manifest.lines.findAll { it.startsWith('~') } == [
                "~ignored ${JavaIgnoreFilter.name}#note".toString(),
                "~ignored ${JavaIgnoreItem.name}#name".toString(),
                "~ignored ${JavaIgnoreLevel.name}#INTERNAL_ONLY".toString(),
                "~ignored ${JavaIgnoreQuery.name}#helper(java.lang.String)".toString()
        ]
    }

    void "groovy: the same cases, with the annotation on a property field"() {
        given:
        startContext(SCHEMA, SPEC_GROOVY)

        when:
        def result = executeQuery('{ item { name label } level describe(filter: {text: "t"}) }')

        then:
        result.errors.isEmpty()
        result.data == [item: [name: 'resolver', label: 'label'], level: 'HIGH', describe: 't']
        applicationContext.getBean(BindingManifest).diagnostics.isEmpty()
    }

    void "a real unbound method still fails strict mode next to the ignored ones"() {
        given:
        startContext(SCHEMA, SPEC_UNBOUND)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.STRICT_FINDINGS
        e.cause.problems == ["The resolver method ${UnboundQuery.name}#stray(java.lang.String) binds no schema field. Remove it, make it non-public, or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.".toString()]
    }

    void "an ignored resolver method does not bind the field of its name"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  helper: String
}
""", SPEC_IGNORED_ROOT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.FIELD_NOT_BOUND
    }

    void "an ignored getter does not bind the field of its name, and the precedence fails only on a real ambiguity"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  item: Item!
}

type Item {
  name: String!
}
""", SPEC_IGNORED_GETTER)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() in [BindingCode.BINDING_FAILURES, BindingCode.FIELD_NOT_BOUND]
        e.cause.code() != BindingCode.PRECEDENCE_AMBIGUITY
    }

    void "an ignored method that carries a coordinate does not switch its bean to coordinate-only binding"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  hello: String
}
""", SPEC_IGNORED_COORDINATE)

        when:
        def result = executeQuery('{ hello }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'hi']
    }

    @Requires(property = 'spec.name', value = SPEC_IGNORED_COORDINATE)
    @GraphQLRootResolver
    static class IgnoredCoordinateQuery {
        String hello() { 'hi' }

        @GraphQLIgnore
        @GraphQLSchemaCoordinate("Query.other")
        String other() { 'other' }
    }

    @Requires(property = 'spec.name', value = SPEC_GROOVY)
    @GraphQLRootResolver
    static class GroovyQuery {
        GroovyItem item() { new GroovyItem() }

        GroovyLevel level() { GroovyLevel.HIGH }

        String describe(GroovyFilter filter) { filter.text }

        @EventListener
        void onStartup(StartupEvent event) {
        }

        @Scheduled(initialDelay = '1h', fixedDelay = '1h')
        void tick() {
        }

        @PreDestroy
        void close() {
        }

        @GraphQLIgnore
        String helper(String value) { value }
    }

    @GraphQLType
    static class GroovyItem {
        @GraphQLIgnore
        String name = 'getter'
        String label = 'label'
    }

    @Requires(property = 'spec.name', value = SPEC_GROOVY)
    @GraphQLTypeResolver(GroovyItem.class)
    static class GroovyItemResolver {
        String name(GroovyItem item) { 'resolver' }
    }

    @GraphQLInput
    static class GroovyFilter {
        String text
        @GraphQLIgnore
        String note
    }

    @Introspected
    static enum GroovyLevel {
        LOW, HIGH, @GraphQLIgnore INTERNAL_ONLY
    }

    @Requires(property = 'spec.name', value = SPEC_UNBOUND)
    @GraphQLRootResolver
    static class UnboundQuery {
        JavaIgnoreItem item() { new JavaIgnoreItem() }

        JavaIgnoreLevel level() { JavaIgnoreLevel.HIGH }

        String describe(JavaIgnoreFilter filter) { filter.text }

        @EventListener
        void onStartup(StartupEvent event) {
        }

        @GraphQLIgnore
        String helper(String value) { value }

        String stray(String value) { value }
    }

    @Requires(property = 'spec.name', value = SPEC_UNBOUND)
    @GraphQLTypeResolver(JavaIgnoreItem.class)
    static class UnboundItemResolver {
        String name(JavaIgnoreItem item) { 'resolver' }
    }

    @Requires(property = 'spec.name', value = SPEC_IGNORED_ROOT)
    @GraphQLRootResolver
    static class IgnoredRootQuery {
        @GraphQLIgnore
        String helper() { 'helper' }
    }

    @Requires(property = 'spec.name', value = SPEC_IGNORED_GETTER)
    @GraphQLRootResolver
    static class IgnoredGetterQuery {
        JavaIgnoreItem item() { new JavaIgnoreItem() }
    }

}
