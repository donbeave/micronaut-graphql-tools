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

/**
 * SDL object, interface and union types that no field reachable from a root operation type returns have no binding.
 * By default they fail the startup, all of them in one error; {@code micronaut.graphql.tools.allow-unreachable} exempts named types and
 * {@code micronaut.graphql.tools.unreachable-types=ignore} exempts all of them.
 */
class UnreachableTypesSpec extends AbstractTest {

    static final String SPEC_NAME = "UnreachableTypesSpec"

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
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Lonely', 'Orphan', 'Shape', 'Stray']
        e.cause.message == """The schema defines types that no field reachable from a root operation type returns, so nothing binds them: Lonely (type), Orphan (type), Shape (interface), Stray (union).
  Bind them through a field, remove them from the schema, list them in micronaut.graphql.tools.allow-unreachable, or set micronaut.graphql.tools.unreachable-types to ignore."""
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

    void "an interface that a reachable type implements but no field returns is reported"() {
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
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Node']
        e.cause.message.startsWith("The schema defines types that no field reachable from a root operation type " +
                "returns, so nothing binds them: Node (interface).")
    }

    void "unreachable-types=fail can be set explicitly"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.unreachable-types': 'fail'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Lonely', 'Orphan', 'Shape', 'Stray']
    }

    void "allow-unreachable exempts exactly the listed types"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.allow-unreachable': ['Orphan', 'Stray']])

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
                ['micronaut.graphql.tools.allow-unreachable': ['Stray', 'Orphan', 'Lonely', 'Shape']])

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
                    "micronaut.graphql.tools.allow-unreachable use graphql-java's default data fetchers: " +
                    "Lonely, Orphan, Shape, Stray"
        }
    }

    void "unreachable-types=ignore lets the schema build and logs every ignored type"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.unreachable-types': 'ignore'])

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
            it.level == Level.WARN && it.formattedMessage == "micronaut.graphql.tools.unreachable-types is ignore: " +
                    "the schema defines types that no field reachable from a root operation type returns; they use " +
                    "graphql-java's default data fetchers: Lonely, Orphan, Shape, Stray"
        }
    }

    void "an allow-unreachable entry that is not an unreachable type fails the startup (#mode)"() {
        given:
        startContext(SCHEMA, SPEC_NAME, [
                'micronaut.graphql.tools.unreachable-types': mode,
                'micronaut.graphql.tools.allow-unreachable': ['Orphan', 'Missing', 'Item', 'Color', 'Filter', 'String']
        ])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidAllowUnreachableException
        e.cause.undefinedTypeNames == ['Missing']
        e.cause.notUnreachableTypeNames == ['Color', 'Filter', 'Item', 'String']
        e.cause.message == """micronaut.graphql.tools.allow-unreachable lists types that are not unreachable object, interface or union types of the schema: Missing (not defined in the schema), Color (not an unreachable object, interface or union type), Filter (not an unreachable object, interface or union type), Item (not an unreachable object, interface or union type), String (not an unreachable object, interface or union type).
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

    @GraphQLType
    static class Item {
        String name = 'item'
    }

}
