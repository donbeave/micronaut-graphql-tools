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

import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.manifest.BindingManifest
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException

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
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.STRICT_FINDINGS
        e.cause.message == """[strict-findings] The strict checks found problems in the binding of the schema:
  1) [unbound-resolver-method] The resolver method ${ItemResolver.name}#legacyName(${Item.name}) binds no schema field. Remove it, make it private, or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.
  2) [unbound-resolver-method] The resolver method ${Query.name}#helper(java.lang.String) binds no schema field. Remove it, make it private, or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.
  3) [unbound-enum-constant] The enum constant ${Color.name}#BLUE has no value in the schema enum Color. Add the value to the schema or remove the constant.
  4) [unused-dictionary-entry] The SchemaMappingDictionary maps Ghost to ${Item.name}, but the schema binds no type Ghost. Remove the entry or correct its name.
  Fix them, allow them in micronaut.graphql.tools.allow.findings, or set micronaut.graphql.tools.checks.<code> to warn or ignore."""
        e.cause.problems.size() == 4
        e.cause.code() == BindingCode.STRICT_FINDINGS
        e.cause.codes == [BindingCode.UNBOUND_RESOLVER_METHOD, BindingCode.UNBOUND_RESOLVER_METHOD,
                          BindingCode.UNBOUND_ENUM_CONSTANT, BindingCode.UNUSED_DICTIONARY_ENTRY]
    }

    void "strict mode can be set explicitly"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': true])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.STRICT_FINDINGS
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
                "!unbound-enum-constant ${Color.name}#BLUE".toString(),
                "!unbound-resolver-method ${ItemResolver.name}#legacyName(${Item.name})".toString(),
                "!unbound-resolver-method ${Query.name}#helper(java.lang.String)".toString(),
                "!unused-dictionary-entry Ghost = ${Item.name}".toString()
        ]
    }

    void "with the manifest not recorded strict mode still fails with the same problems"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.manifest.record': false])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.STRICT_FINDINGS
        e.cause.problems.size() == 4
    }

    void "with strict false and the manifest not recorded the schema binds, and the manifest bean says how to get it"() {
        given:
        startContext(SCHEMA, SPEC_NAME, NOT_STRICT + ['micronaut.graphql.tools.manifest.record': false])

        when:
        def result = executeQuery('{ item { name color } }')

        then:
        result.errors.isEmpty()
        result.data == [item: [name: 'pen', color: 'GREEN']]

        when:
        applicationContext.getBean(BindingManifest)

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof NoSuchElementException
        e.cause.message.contains('micronaut.graphql.tools.manifest.record')
    }

    void "the manifest is recorded by manifest.record, or by manifest.enabled"() {
        given:
        startContext(SCHEMA, SPEC_NAME, NOT_STRICT + config)

        expect:
        !applicationContext.getBean(BindingManifest).lines.isEmpty()

        where:
        config << [
                ['micronaut.graphql.tools.manifest.record': true],
                ['micronaut.graphql.tools.manifest.record': false, 'micronaut.graphql.tools.manifest.enabled': true,
                 'micronaut.graphql.tools.manifest.path'  : File.createTempFile('manifest', '.txt').absolutePath]
        ]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
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

    @Introspected
    static enum Color {
        RED, GREEN, BLUE
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
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
    @Singleton
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
""", SPEC_NAME + ".unreachable", ['micronaut.graphql.tools.allow.unreachable': ['Draft']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.STRICT_FINDINGS
        e.cause.problems == ["The resolver method ${DraftResolver.name}#note(${Draft.name}) binds no schema field. Remove it, make it private, or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.".toString()]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unreachable")
    @Singleton
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
    @Singleton
    @GraphQLTypeResolver(Draft.class)
    static class DraftResolver {
        @GraphQLSchemaCoordinate("Draft.note")
        String note(Draft draft) {
            return draft.text
        }
    }

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
                "!precedence-ambiguity Item.name ; ${Item.name}#name | ${ShadowingResolver.name}#name(${Item.name})".toString()
        ]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".shadowed")
    @Singleton
    @GraphQLRootResolver
    static class ShadowedQuery {
        Item item() {
            return new Item(name: 'pen')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".shadowed")
    @Singleton
    @GraphQLTypeResolver(Item.class)
    static class ShadowingResolver {
        String name(Item item) {
            return 'resolved'
        }
    }

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
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNREACHABLE_TYPE
        e.cause.typeNames == ['Filter', 'Long', 'Orphan', 'Size']
        e.cause.message.startsWith('[unreachable-type] The schema defines types that no field reachable from a root operation type ' +
                'returns, so nothing binds them: Filter (input), Long (scalar), Orphan (type), Size (enum).')
    }

    void "in strict mode allow-unreachable may name enum, input and scalar types"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes",
                ['micronaut.graphql.tools.allow.unreachable': ['Orphan', 'Size', 'Filter', 'Long']])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics ==
                ['!unreachable-type Orphan', '!unreachable-value-type Filter', '!unreachable-value-type Long', '!unreachable-value-type Size']
    }

    void "in strict mode an allow-unreachable entry naming a reached enum fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.allow.unreachable': ['Color']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.INVALID_ALLOW_UNREACHABLE
        e.cause.message == """[invalid-allow-unreachable] micronaut.graphql.tools.allow.unreachable lists types that are not unreachable object, interface, union, enum, input or scalar types of the schema: Color (is reachable).
  Remove them from the list."""
    }

    void "with unreachable-types ignore, unreachable enum, input and scalar types are ignored too"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes", ['micronaut.graphql.tools.checks.unreachable-type': 'warn', 'micronaut.graphql.tools.checks.unreachable-value-type': 'warn'])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
    }

    void "with strict false only unreachable object, interface and union types fail, as before"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes",
                ['micronaut.graphql.tools.allow.unreachable': ['Orphan']] + NOT_STRICT)

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics ==
                ['!unreachable-type Orphan', '!unreachable-value-type Filter', '!unreachable-value-type Long', '!unreachable-value-type Size']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".valueTypes")
    @Singleton
    @GraphQLRootResolver
    static class ValueTypesQuery {
        String label() {
            return 'label'
        }
    }

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
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.STRICT_FINDINGS
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
    @Singleton
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
    @Singleton
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
""", SPEC_NAME + ".directives", ['micronaut.graphql.tools.allow.inert-directives': ['restricted']])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics.isEmpty()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".directives")
    @Singleton
    @GraphQLRootResolver
    static class DirectiveQuery {
        String label() {
            return 'label'
        }
    }

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
    @Singleton
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
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.STRICT_FINDINGS
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
    @Singleton
    @GraphQLRootResolver
    static class SharedPing {
        String ping() {
            return 'pong'
        }
    }

    @Language("GraphQL")
    static final String TRANSITIVE_SCHEMA = """
schema {
  query: Query
}

scalar Long

type Query {
  label: String
}

type Legacy {
  since: Long
  era: Era
  search(filter: Filter): String
}

type Stray {
  size: Size
}

enum Era {
  OLD
}

enum Grade {
  A
}

enum Size {
  SMALL
}

input Filter {
  grade: Grade
  nested: Nested
}

input Nested {
  era: Era
}
"""

    void "allowing only an unreachable object type covers the enums, scalars and inputs its fields and arguments use"() {
        given:
        startContext(TRANSITIVE_SCHEMA, SPEC_NAME + ".transitive",
                ['micronaut.graphql.tools.allow.unreachable': ['Legacy', 'Stray']])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
    }

    void "with unreachable-types ignore the value types of ignored types need no entry"() {
        given:
        startContext(TRANSITIVE_SCHEMA, SPEC_NAME + ".transitive", ['micronaut.graphql.tools.checks.unreachable-type': 'warn', 'micronaut.graphql.tools.checks.unreachable-value-type': 'warn'])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
    }

    void "a type that only a non-allowed unreachable type uses still fails"() {
        given:
        startContext(TRANSITIVE_SCHEMA, SPEC_NAME + ".transitive", ['micronaut.graphql.tools.allow.unreachable': ['Legacy']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNREACHABLE_TYPE
        e.cause.typeNames == ['Size', 'Stray']
    }

    void "a stale allow-unreachable entry still fails, also next to an allowed type that covers others"() {
        given:
        startContext(TRANSITIVE_SCHEMA, SPEC_NAME + ".transitive",
                ['micronaut.graphql.tools.allow.unreachable': ['Legacy', 'Stray', 'Query', 'Missing']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.INVALID_ALLOW_UNREACHABLE
        e.cause.undefinedTypeNames == ['Missing']
        e.cause.notUnreachableTypeNames == ['Query']
    }

    void "a value type that an allowed type covers may still be listed itself, and the manifest still reports it"() {
        given:
        startContext(TRANSITIVE_SCHEMA, SPEC_NAME + ".transitive",
                ['micronaut.graphql.tools.allow.unreachable': ['Legacy', 'Stray', 'Era']])

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics.contains('!unreachable-value-type Era')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".transitive")
    @Singleton
    @GraphQLRootResolver
    static class TransitiveQuery {
        String label() {
            return 'label'
        }
    }

    void "with strict false allow-unreachable may still name unreachable enum, input and scalar types, without effect"() {
        given:
        startContext(VALUE_TYPES_SCHEMA, SPEC_NAME + ".valueTypes",
                ['micronaut.graphql.tools.allow.unreachable': ['Orphan', 'Size', 'Filter', 'Long']] + NOT_STRICT)

        when:
        def result = executeQuery('{ label }')

        then:
        result.data == [label: 'label']
        applicationContext.getBean(BindingManifest).diagnostics ==
                ['!unreachable-type Orphan', '!unreachable-value-type Filter', '!unreachable-value-type Long', '!unreachable-value-type Size']
    }

}
