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
package io.micronaut.graphql.tools

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.InvalidAllowUnreachableException
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

class UnreachableTypesSpec extends AbstractTest {

    static final String SPEC_NAME = "UnreachableTypesSpec"
    static final String SPEC_NAME_EXTENSION = "UnreachableTypesSpecExtension"

    static final Map NOT_STRICT = ['micronaut.graphql.tools.checks.unreachable-value-type': 'ignore']

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  item: Item
}

type Item {
  name: String
}

type Orphan {
  id: ID
}

type Lonely {
  id: ID
  orphan: Orphan
}

union Stray = Orphan | Lonely

interface Shape {
  area: Float
}

enum Color {
  RED
}

input Filter {
  name: String
}
"""

    ListAppender<ILoggingEvent> appender

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        generatorLogger().addAppender(appender)
    }

    void cleanup() {
        generatorLogger().detachAppender(appender)
    }

    void "by default all unreachable types fail the startup in one error"() {
        given:
        startContext(SCHEMA, SPEC_NAME, NOT_STRICT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Lonely', 'Orphan', 'Shape', 'Stray']
        e.cause.message == """[unreachable-type] The schema defines types that no field reachable from a root operation type returns, so nothing binds them: Lonely (type), Orphan (type), Shape (interface), Stray (union).
  Bind them through a field, remove them from the schema, list them in micronaut.graphql.tools.allow.unreachable, or set micronaut.graphql.tools.checks.unreachable-type to warn or ignore."""
    }

    void "unreachable object types are all reported, not only the first one"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  item: Item
}

type Item {
  name: String
}

type Volume {
  amount: Float
}

type Archive {
  size: Int
}
"""
        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Archive', 'Volume']
    }

    void "an interface that a reachable type implements is reached, although no field returns it"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  item: Item
}

interface Node {
  name: String
}

type Item implements Node {
  name: String
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    item { name }
    __type(name: "Node") { kind possibleTypes { name } }
}
""")

        then:
        result.errors.isEmpty()
        result.data.item == [name: 'item']
        result.data.__type == [kind: 'INTERFACE', possibleTypes: [[name: 'Item']]]
    }

    void "checks.unreachable-type=fail can be set explicitly"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.checks.unreachable-type': 'fail'] + NOT_STRICT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Lonely', 'Orphan', 'Shape', 'Stray']
    }

    void "allow-unreachable exempts exactly the listed types"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.allow.unreachable': ['Orphan', 'Stray']] + NOT_STRICT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Lonely', 'Shape']
    }

    void "allow-unreachable listing every unreachable type lets the schema build, and the exemption is logged"() {
        given:
        startContext(SCHEMA, SPEC_NAME,
                ['micronaut.graphql.tools.allow.unreachable': ['Stray', 'Orphan', 'Lonely', 'Shape']] + NOT_STRICT)

        when:
        def result = executeQuery("""
{
    item { name }
    orphan: __type(name: "Orphan") { name }
}
""")

        then:
        result.errors.isEmpty()
        result.data.item == [name: 'item']
        result.data.orphan == [name: 'Orphan']
        appender.list.any {
            it.level == Level.INFO && it.formattedMessage == "Unreachable types allowed by " +
                    "micronaut.graphql.tools.allow.unreachable use graphql-java's default data fetchers: " +
                    "Lonely, Orphan, Shape, Stray"
        }
    }

    void "an allow-unreachable entry names why a listed type is not unreachable"() {
        given:
        startContext(SCHEMA + '''
directive @sized(size: Size) on FIELD_DEFINITION

enum Size {
  LARGE
}
''', SPEC_NAME, [
                'micronaut.graphql.tools.allow.unreachable': ['Query', 'Item', 'Size', 'String']
        ] + NOT_STRICT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidAllowUnreachableException
        e.cause.notUnreachableTypeNames == ['Item', 'Query', 'Size', 'String']
        e.cause.message.contains('Item (is reachable), Query (is reachable), Size (is used by a directive), String (is a built-in scalar)')
    }

    void "a type that an extension field returns is reachable, so it is not reported"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  title: String
}

extend type Query {
  note: Note
}

type Note {
  text: String
}
"""
        startContext(schema, SPEC_NAME_EXTENSION)

        when:
        def result = executeQuery("{ title note { text } }")

        then:
        result.errors.isEmpty()
        result.data == [title: 'title', note: [text: 'text']]
    }

    void "checks.unreachable-type=ignore logs no finding for the unreachable types"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.checks.unreachable-type': 'ignore'] + NOT_STRICT)

        when:
        def result = executeQuery("""
{
    item { name }
}
""")

        then:
        result.errors.isEmpty()
        appender.list.findAll { it.formattedMessage.contains('unreachable') && it.level.isGreaterOrEqual(Level.WARN) }.isEmpty()
    }

    void "checks.unreachable-type=warn lets the schema build and logs every unreachable type"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.checks.unreachable-type': 'warn'] + NOT_STRICT)

        when:
        def result = executeQuery("""
{
    item { name }
    __schema { types { name } }
}
""")

        then:
        result.errors.isEmpty()
        result.data.item == [name: 'item']
        result.data.__schema.types*.name.containsAll(['Lonely', 'Orphan', 'Shape', 'Stray'])
        appender.list.any {
            it.level == Level.WARN && it.formattedMessage == "[unreachable-type] " +
                    "micronaut.graphql.tools.checks.unreachable-type is warn, so the startup goes on:\n  " +
                    "Lonely (type)\n  Orphan (type)\n  Shape (interface)\n  Stray (union)"
        }
    }

    void "an allow-unreachable entry that is not an unreachable type fails the startup (#mode)"() {
        given:
        startContext(SCHEMA, SPEC_NAME, [
                'micronaut.graphql.tools.checks.unreachable-type': mode,
                'micronaut.graphql.tools.allow.unreachable': ['Orphan', 'Missing', 'Item', 'Color', 'Filter', 'String']
        ] + NOT_STRICT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidAllowUnreachableException
        e.cause.undefinedTypeNames == ['Missing']
        e.cause.notUnreachableTypeNames == ['Item', 'String']
        e.cause.message == """[invalid-allow-unreachable] micronaut.graphql.tools.allow.unreachable lists types that are not unreachable object, interface, union, enum, input or scalar types of the schema: Missing (not defined in the schema), Item (is reachable), String (is a built-in scalar).
  Remove them from the list."""

        where:
        mode << ['fail', 'ignore']
    }

    void "a schema without unreachable types starts with the defaults"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  item: Item
}

type Item {
  name: String
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("{ item { name } }")

        then:
        result.errors.isEmpty()
        result.data.item == [name: 'item']
    }

    private static Logger generatorLogger() {
        return (Logger) LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Item item() {
            return new Item()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_EXTENSION)
    @GraphQLRootResolver
    static class ExtensionQuery {
        String title() {
            return 'title'
        }

        Note note() {
            return new Note()
        }
    }

    @GraphQLType
    static class Note {
        String getText() {
            return 'text'
        }
    }

    @GraphQLType
    static class Item {
        String name = 'item'
    }

}
