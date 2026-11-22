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
package io.micronaut.graphql.tools.mapping.precedence

import jakarta.inject.Singleton
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.internal.wiring.GraphQLRuntimeWiringGenerator
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

class FieldBindingPrecedenceSpec extends AbstractTest {

    static final String SPEC_NAME = "FieldBindingPrecedenceSpec"

    static final AtomicInteger RESOLVER_CALLS = new AtomicInteger()

    @Language("GraphQL")
    static final String MARKED_SCHEMA = """
schema {
  query: Query
}

directive @computed on FIELD_DEFINITION

scalar Long

type Query {
  items: [Item!]!
}

type Item {
  name: String!
  price: Long! @computed
}
"""

    @Language("GraphQL")
    static final String UNMARKED_SCHEMA = """
schema {
  query: Query
}

directive @computed on FIELD_DEFINITION

scalar Long

type Query {
  items: [Item!]!
}

type Item {
  name: String!
  price: Long!
}
"""

    void setup() {
        RESOLVER_CALLS.set(0)
    }

    void "a field marked with a resolver directive binds the type resolver method, not the getter"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.resolver-directives': ['computed']])

        when:
        def result = executeQuery('{ items { name price } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[name: 'pen', price: 3L], [name: 'ink', price: 3L]]
        RESOLVER_CALLS.get() == 2
    }

    void "without the directive, a getter and a type resolver method for one field fail the startup"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.PRECEDENCE_AMBIGUITY
        e.cause.message == """[precedence-ambiguity] Both a member of the Java type and a type resolver method can bind one GraphQL field.
  GraphQL coordinate: Item.price (line 16, column 3)
  Members:
  1) ${Item.name} getPrice()
  2) ${ItemResolver.name} price(${Item.name} item)
  Fix it for this field: put a directive listed in micronaut.graphql.tools.resolver-directives on the field, declare @GraphQLSchemaCoordinate on the type resolver method (the coordinate of the type, or of the interface that declares the field), or annotate the Java member with @GraphQLIgnore. Fix it for every field: set micronaut.graphql.tools.precedence to resolver-first or property-first. micronaut.graphql.tools.strict does not change this."""
    }

    void "precedence fail can be set explicitly"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'fail'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.PRECEDENCE_AMBIGUITY
        e.cause.message.contains('GraphQL coordinate: Item.price ')
        e.cause.message.contains("${Item.name} getPrice()")
        e.cause.message.contains("${ItemResolver.name} price(${Item.name} item)")
    }

    void "the old precedence value strict is rejected and the message names fail"() {
        when:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'strict'])
        getGraphQLBean()

        then:
        def e = thrown(Exception)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.REMOVED_CONFIGURATION_KEY
        generateMessage(e).contains('micronaut.graphql.tools.precedence')
        generateMessage(e).contains('strict')
        generateMessage(e).contains('fail')
    }

    void "strict false does not relax the ambiguity"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': false])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.PRECEDENCE_AMBIGUITY
        e.cause.message.contains('GraphQL coordinate: Item.price ')
        e.cause.message.contains("${Item.name} getPrice()")
        e.cause.message.contains("${ItemResolver.name} price(${Item.name} item)")
    }

    void "strict false does not change precedence resolver-first or property-first"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': false,
                                                  'micronaut.graphql.tools.precedence': precedence])

        when:
        def result = executeQuery('{ items { name price } }')

        then:
        (result.errors.isEmpty()) == (precedence == 'resolver-first')
        RESOLVER_CALLS.get() == (precedence == 'resolver-first' ? 2 : 0)

        where:
        precedence << ['resolver-first', 'property-first']
    }

    void "a type resolver method with an explicit coordinate wins, whatever the precedence is (#precedence)"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME + '.coordinate', precedence == null ? [:] :
                ['micronaut.graphql.tools.precedence': precedence])

        when:
        def result = executeQuery('{ items { name price } }')
        def manifest = applicationContext.getBean(io.micronaut.graphql.tools.manifest.BindingManifest)

        then:
        result.errors.isEmpty()
        result.data.items == [[name: 'pen', price: 3L], [name: 'ink', price: 3L]]
        RESOLVER_CALLS.get() == 2
        manifest.diagnostics.findAll { it.startsWith('!precedence-ambiguity') }.isEmpty()

        where:
        precedence << [null, 'fail', 'resolver-first', 'property-first']
    }

    private static String generateMessage(Throwable e) {
        def messages = []
        for (Throwable t = e; t != null; t = t.cause) {
            messages << t.message
        }
        return messages.join('\n')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.coordinate')
    @Singleton
    @GraphQLRootResolver
    static class CoordinateQuery {
        List<Item> items() {
            return [new Item(name: 'pen'), new Item(name: 'ink')]
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.coordinate')
    @Singleton
    @GraphQLTypeResolver(Item.class)
    static class CoordinateItemResolver {
        @GraphQLSchemaCoordinate("Item.price")
        CompletionStage<Long> price(Item item) {
            RESOLVER_CALLS.incrementAndGet()
            return CompletableFuture.completedFuture(3L)
        }
    }

    void "precedence property-first binds the getter and never calls the type resolver method, as before the option"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'property-first'])

        when:
        def result = executeQuery('{ items { name price } }')

        then: 'the getter returns null for a non-null field'
        result.data == null
        !result.errors.isEmpty()
        result.errors*.message.every { it.contains('non null') }
        RESOLVER_CALLS.get() == 0
    }

    void "precedence resolver-first binds the type resolver method"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'resolver-first'])

        when:
        def result = executeQuery('{ items { name price } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[name: 'pen', price: 3L], [name: 'ink', price: 3L]]
        RESOLVER_CALLS.get() == 2
    }

    void "the directive wins over precedence property-first"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME, [
                'micronaut.graphql.tools.resolver-directives': ['computed'],
                'micronaut.graphql.tools.precedence'         : 'property-first'
        ])

        when:
        def result = executeQuery('{ items { price } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[price: 3L], [price: 3L]]
    }

    void "resolver-first and property-first log the ambiguity at WARN with the field and both members (#precedence)"() {
        given:
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        def logger = (Logger) LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator)
        logger.addAppender(appender)

        when:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': precedence])
        getGraphQLBean()

        then:
        def warnings = appender.list.findAll { it.level == Level.WARN }*.formattedMessage
        warnings.size() == 1
        warnings[0].contains("micronaut.graphql.tools.precedence is ${precedence}")
        warnings[0].contains('Item.price')
        warnings[0].contains("${Item.name} getPrice()")
        warnings[0].contains("${ItemResolver.name} price(${Item.name} item)")

        cleanup:
        logger.detachAppender(appender)

        where:
        precedence << ['resolver-first', 'property-first']
    }

    void "a directive that is not listed has no effect"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.PRECEDENCE_AMBIGUITY
        e.cause.message.contains('GraphQL coordinate: Item.price ')
        e.cause.message.contains("${Item.name} getPrice()")
        e.cause.message.contains("${ItemResolver.name} price(${Item.name} item)")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        List<Item> items() {
            return [new Item(name: 'pen'), new Item(name: 'ink')]
        }
    }

    @GraphQLType
    static class Item {
        String name
        Long price
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLTypeResolver(Item.class)
    static class ItemResolver {
        CompletionStage<Long> price(Item item) {
            RESOLVER_CALLS.incrementAndGet()
            return CompletableFuture.completedFuture(3L)
        }
    }

    void "a field marked with a resolver directive fails the startup when only a getter binds it"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME + ".getterOnly", ['micronaut.graphql.tools.resolver-directives': ['computed']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.RESOLVER_REQUIRED
        e.cause.message == """[resolver-required] The GraphQL field carries @computed, so a type resolver method must bind it, but none does.
  GraphQL coordinate: Item.price (line 16, column 3)
  Not bound:
  1) ${Item.name} getPrice()
  Add a type resolver method for the field, or remove @computed from it."""
        e.cause.directive == 'computed'
    }

    void "without the directive a getter alone binds the field"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME + ".getterOnly")

        when:
        def result = executeQuery('{ items { name } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[name: 'pen'], [name: 'ink']]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".getterOnly")
    @Singleton
    @GraphQLRootResolver
    static class GetterOnlyQuery {
        List<Item> items() {
            return [new Item(name: 'pen', price: 1L), new Item(name: 'ink', price: 2L)]
        }
    }

    void "a @GraphQLField method and a type resolver method for one field fail the startup"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  label: Label
}

type Label {
  text(upper: Boolean): String
}
""", SPEC_NAME + ".method")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.PRECEDENCE_AMBIGUITY
        e.cause.message.contains('GraphQL coordinate: Label.text ')
        e.cause.message.contains("1) ${Label.name} text(java.lang.Boolean upper)")
        e.cause.message.contains("2) ${LabelResolver.name} text(${Label.name} label, java.lang.Boolean upper)")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".method")
    @Singleton
    @GraphQLRootResolver
    static class LabelQuery {
        Label label() {
            return new Label()
        }
    }

    @GraphQLType
    static class Label {
        @GraphQLField
        String text(Boolean upper) {
            return upper ? 'TEXT' : 'text'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".method")
    @Singleton
    @GraphQLTypeResolver(Label.class)
    static class LabelResolver {
        String text(Label label, Boolean upper) {
            return 'resolved'
        }
    }

    void "a resolver directive that the schema does not declare fails the startup"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME + ".getterOnly", ['micronaut.graphql.tools.resolver-directives': ['computed', 'missing', 'absent']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ConfigurationException
        e.cause.message == "micronaut.graphql.tools.resolver-directives lists directives that the schema does not declare: absent, missing."
    }

}
