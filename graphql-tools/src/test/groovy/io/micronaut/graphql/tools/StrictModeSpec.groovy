package io.micronaut.graphql.tools

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.InvalidAllowUnreachableException
import io.micronaut.graphql.tools.exceptions.StrictModeException
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * Strict mode (on by default) fails the startup on what the binding manifest reports as a finding and no option
 * allows; {@code micronaut.graphql.tools.strict=false} keeps the findings in the manifest only.
 */
class StrictModeSpec extends AbstractTest {

    static final String SPEC_NAME = "StrictModeSpec"

    static final Map NOT_STRICT = ['micronaut.graphql.tools.strict': false]

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  item: Item
}

type Item {
  name: String!
  color: Color!
}

enum Color {
  RED
  GREEN
}
"""

    void "a resolver method that binds no field, an enum constant without a schema value and an unused dictionary entry fail the startup in one error"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof StrictModeException
        e.cause.message == """Strict mode found problems in the binding of the schema:
  1) The resolver method ${ItemResolver.name}#legacyName(${Item.name}) binds no schema field. Remove it, make it non-public, or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.
  2) The resolver method ${Query.name}#helper(java.lang.String) binds no schema field. Remove it, make it non-public, or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.
  3) The enum constant ${Color.name}#BLUE has no value in the schema enum Color. Add the value to the schema or remove the constant.
  4) The SchemaMappingDictionary maps Ghost to ${Item.name}, but the schema binds no type Ghost. Remove the entry or correct its name.
  Fix them, or set micronaut.graphql.tools.strict to false."""
        e.cause.problems.size() == 4
    }

    void "strict mode can be set explicitly"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': true])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof StrictModeException
    }

    void "with strict false the schema binds as before and the findings are only listed in the manifest"() {
        given:
        startContext(SCHEMA, SPEC_NAME, NOT_STRICT)

        when:
        def result = executeQuery('{ item { name color } }')

        then:
        result.errors.isEmpty()
        result.data == [item: [name: 'pen', color: 'GREEN']]
        applicationContext.getBean(BindingManifest).diagnostics == [
                "!unbound ${Color.name}#BLUE".toString(),
                "!unbound ${ItemResolver.name}#legacyName(${Item.name})".toString(),
                "!unbound ${Query.name}#helper(java.lang.String)".toString(),
                "!unusedDictionary Ghost = ${Item.name}".toString()
        ]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Item item() {
            return new Item(name: 'pen', color: Color.GREEN)
        }

        String helper(String value) {
            return value
        }
    }

    @GraphQLType
    static class Item {
        String name
        Color color
    }

    static enum Color {
        RED, GREEN, BLUE
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Item.class)
    static class ItemResolver {
        String legacyName(Item item) {
            return item.name
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary.registerType("Ghost", Item.class)
        }
    }

    // --- resolver contracts ---

    void "on a bean that uses coordinates only the methods with a coordinate must bind"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  label: String
}
""", SPEC_NAME + ".coordinates")

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".coordinates")
    @GraphQLRootResolver
    static class LabelQuery {
        @GraphQLSchemaCoordinate("Query.label")
        String label() {
            return format('label')
        }

        String format(String value) {
            return value
        }
    }

    void "a coordinate method of an allowed unreachable type fails the startup"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  label: String
}

type Draft {
  note: String
}
""", SPEC_NAME + ".unreachable", ['micronaut.graphql.tools.allow-unreachable': ['Draft']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof StrictModeException
        e.cause.problems == ["The resolver method ${DraftResolver.name}#note(${Draft.name}) binds no schema field. Remove it, make it non-public, or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.".toString()]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unreachable")
    @GraphQLRootResolver
    static class DraftQuery {
        String label() {
            return 'label'
        }
    }

    @GraphQLType
    static class Draft {
        String text
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unreachable")
    @GraphQLTypeResolver(Draft.class)
    static class DraftResolver {
        @GraphQLSchemaCoordinate("Draft.note")
        String note(Draft draft) {
            return draft.text
        }
    }

    // --- a resolver method that the precedence leaves unbound ---

    void "a type resolver method that precedence property-first leaves unbound is not reported"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  item: Item
}

type Item {
  name: String!
}
""", SPEC_NAME + ".shadowed", ['micronaut.graphql.tools.precedence': 'property-first'])

        when:
        def result = executeQuery('{ item { name } }')

        then:
        result.data == [item: [name: 'pen']]
        applicationContext.getBean(BindingManifest).diagnostics == [
                "!ambiguous Item.name · ${Item.name}#getName() | ${ShadowingResolver.name}#name(${Item.name})".toString()
        ]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".shadowed")
    @GraphQLRootResolver
    static class ShadowedQuery {
        Item item() {
            return new Item(name: 'pen')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".shadowed")
    @GraphQLTypeResolver(Item.class)
    static class ShadowingResolver {
        String name(Item item) {
            return 'resolved'
        }
    }

    // --- unreachable enum, input and scalar types ---

    @Language("GraphQL")
    static final String VALUE_TYPES_SCHEMA = """
schema {
  query: Query
}

scalar Long

type Query {
  label: String
}

type Orphan {
  id: ID
}

enum Size {
  SMALL
}

input Filter {
  name: String
}
"""

    void "unreachable enum, input and scalar types fail the startup with the other unreachable types"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnreachableTypesException
        e.cause.typeNames == ['Filter', 'Long', 'Orphan', 'Size']
        e.cause.message.startsWith('The schema defines types that no field reachable from a root operation type ' +
                'returns, so nothing binds them: Filter (input), Long (scalar), Orphan (type), Size (enum).')
    }

    void "in strict mode allow-unreachable may name enum, input and scalar types"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes",
                ['micronaut.graphql.tools.allow-unreachable': ['Orphan', 'Size', 'Filter', 'Long']])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics ==
                ['!unreachable Filter', '!unreachable Long', '!unreachable Orphan', '!unreachable Size']
    }

    void "in strict mode an allow-unreachable entry naming a reached enum fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.allow-unreachable': ['Color']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidAllowUnreachableException
        e.cause.message == """micronaut.graphql.tools.allow-unreachable lists types that are not unreachable object, interface, union, enum, input or scalar types of the schema: Color (not an unreachable object, interface, union, enum, input or scalar type).
  Remove them from the list."""
    }

    void "with unreachable-types ignore, unreachable enum, input and scalar types are ignored too"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes", ['micronaut.graphql.tools.unreachable-types': 'ignore'])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
    }

    void "with strict false only unreachable object, interface and union types fail, as before"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes",
                ['micronaut.graphql.tools.allow-unreachable': ['Orphan']] + NOT_STRICT)

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics ==
                ['!unreachable Filter', '!unreachable Long', '!unreachable Orphan', '!unreachable Size']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".valueTypes")
    @GraphQLRootResolver
    static class ValueTypesQuery {
        String label() {
            return 'label'
        }
    }

    // --- Java interfaces without their own introspection ---

    @Language("GraphQL")
    static final String NOTE_SCHEMA = """
schema {
  query: Query
}

type Query {
  note: Note
}

type Note {
  text: String
}
"""

    void "an object type mapped to a Java interface without its own introspection fails the startup"() {
        given:
        startContext(NOTE_SCHEMA, SPEC_NAME + ".plainInterface")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof StrictModeException
        e.cause.problems == ["The type Note is mapped to the interface ${PlainNote.name}, which has no bean introspection of its own, so its properties are read from the implementation ${PlainNoteImpl.name}. Annotate the interface with @GraphQLType or @Introspected.".toString()]
    }

    void "with strict false such an interface is read through its implementation, as before"() {
        given:
        startContext(NOTE_SCHEMA, SPEC_NAME + ".plainInterface", NOT_STRICT)

        when:
        def result = executeQuery('{ note { text } }')

        then:
        result.data == [note: [text: 'written']]
    }

    void "an interface with its own introspection passes"() {
        given:
        startContext(NOTE_SCHEMA, SPEC_NAME + ".introspectedInterface")

        when:
        def result = executeQuery('{ note { text } }')

        then:
        result.data == [note: [text: 'written']]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".plainInterface")
    @GraphQLRootResolver
    static class PlainNoteQuery {
        PlainNote note() {
            return new PlainNoteImpl(text: 'written')
        }
    }

    static interface PlainNote {
    }

    @GraphQLType(PlainNote)
    static class PlainNoteImpl implements PlainNote {
        String text
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".introspectedInterface")
    @GraphQLRootResolver
    static class IntrospectedNoteQuery {
        IntrospectedNote note() {
            return new IntrospectedNoteImpl(text: 'written')
        }
    }

    @Introspected
    static interface IntrospectedNote {
        String getText()
    }

    @GraphQLType(IntrospectedNote)
    static class IntrospectedNoteImpl implements IntrospectedNote {
        String text
    }

    // --- types that only directive definitions use ---

    void "enum, input and scalar types that only directive definitions use are reached"() {
        given:
        startContext("""
schema {
  query: Query
}

scalar Long

directive @restricted(level: Level!, scope: Scope, since: Long) on FIELD_DEFINITION

enum Level {
  LOW
  HIGH
}

input Scope {
  name: String
  tier: Tier
}

enum Tier {
  BASIC
}

type Query {
  label: String @restricted(level: HIGH)
}
""", SPEC_NAME + ".directives", ['micronaut.graphql.tools.inert-directives': ['restricted']])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics.isEmpty()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".directives")
    @GraphQLRootResolver
    static class DirectiveQuery {
        String label() {
            return 'label'
        }
    }

    // --- methods of java.lang.Object ---

    void "overrides of toString, equals and hashCode on a resolver bean are no finding"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  label: String
}
""", SPEC_NAME + ".objectMethods")

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics.isEmpty()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".objectMethods")
    @GraphQLRootResolver
    static class ObjectMethodsQuery {
        String label() {
            return 'label'
        }

        @Override
        String toString() {
            return 'ObjectMethodsQuery'
        }

        @Override
        boolean equals(Object other) {
            return other instanceof ObjectMethodsQuery
        }

        @Override
        int hashCode() {
            return 1
        }
    }

    // --- one root method for several operation types ---

    @Language("GraphQL")
    static final String PING_SCHEMA = """
schema {
  query: Query
  mutation: Mutation
}

type Query {
  ping: String!
}

type Mutation {
  ping: String!
}
"""

    void "a root method without a coordinate that binds the field of its name on several operation types fails the startup"() {
        given:
        startContext(PING_SCHEMA, SPEC_NAME + ".sharedRoot")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof StrictModeException
        e.cause.problems == ["The root resolver method ${SharedPing.name}#ping() binds fields of several operation types: Mutation.ping, Query.ping. Give each operation type its own method with @GraphQLSchemaCoordinate(\"Type.field\").".toString()]
    }

    void "with strict false one root method may bind the field of its name on several operation types, as before"() {
        given:
        startContext(PING_SCHEMA, SPEC_NAME + ".sharedRoot", NOT_STRICT)

        when:
        def query = executeQuery('{ ping }')
        def mutation = executeQuery('mutation { ping }')

        then:
        query.data == [ping: 'pong']
        mutation.data == [ping: 'pong']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".sharedRoot")
    @GraphQLRootResolver
    static class SharedPing {
        String ping() {
            return 'pong'
        }
    }

    // --- allow-unreachable without strict mode ---

    void "with strict false allow-unreachable may still name unreachable enum, input and scalar types, without effect"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes",
                ['micronaut.graphql.tools.allow-unreachable': ['Orphan', 'Size', 'Filter', 'Long']] + NOT_STRICT)

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics ==
                ['!unreachable Filter', '!unreachable Long', '!unreachable Orphan', '!unreachable Size']
    }

}
