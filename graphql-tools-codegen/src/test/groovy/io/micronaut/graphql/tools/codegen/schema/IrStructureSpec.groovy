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
package io.micronaut.graphql.tools.codegen.schema

import io.micronaut.graphql.tools.codegen.Language
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.ir.AbstractType
import io.micronaut.graphql.tools.codegen.ir.shared.Docs
import io.micronaut.graphql.tools.codegen.ir.shared.Operation
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef
import io.micronaut.graphql.tools.codegen.ir.shared.Wrapper
import spock.lang.Specification

class IrStructureSpec extends Specification {

    static final Options OPTIONS = Options.builder().packageName('com.example').build()

    def "nullability is kept on every type reference"() {
        when:
        def field = build('type Query { q: Int }\ntype A { f: [[Int!]]! }').ir().models()[0].fields()[0]

        then:
        field.type() == new TypeRef.ListOf(new TypeRef.ListOf(
                new TypeRef.Builtin(io.micronaut.graphql.tools.codegen.ir.shared.BuiltinScalar.INT, false), true), false)
    }

    def "an interface that implements an interface keeps it, and the members of a sealed interface are the implementers"() {
        when:
        def ir = build('''
            type Query { q: Int }
            interface Node { id: ID! }
            interface Resource implements Node { id: ID! }
            type Book implements Resource & Node { id: ID! }
            type Orphan { id: ID! }
            interface Lonely { id: ID! }
            union Result = Book | Orphan
        ''').ir()
        def byName = ir.abstractTypes().collectEntries { [(it.names().sdlName()): it] }

        then:
        byName.Resource.implemented()*.sdlName() == ['Node']
        byName.Node.members()*.sdlName() == ['Resource', 'Book']
        byName.Node.sealed()
        byName.Resource.members()*.sdlName() == ['Book']
        byName.Lonely.members().empty
        !byName.Lonely.sealed()
        byName.Result.members()*.sdlName() == ['Book', 'Orphan']
        byName.Result.sealed()
        byName.Result.kind() == AbstractType.Kind.UNION
        ir.models().find { it.names().sdlName() == 'Book' }.implemented()*.sdlName() == ['Resource', 'Node']
        ir.models().find { it.names().sdlName() == 'Book' }.memberOf()*.sdlName() == ['Result']
    }

    def "a member that is mapped to an external class makes the union and the interface open"() {
        when:
        def result = IrBuilder.fromSources([new SchemaSource('schema.graphqls', '''
            type Query { q: Int }
            interface Node { id: ID! }
            type Book implements Node { id: ID! }
            type Ext implements Node { id: ID! }
            union Result = Book | Ext
        ''')], OPTIONS.toBuilder().typeMappings(['Ext': 'com.other.Ext']).build())
        def ir = result.ir()
        def byName = ir.abstractTypes().collectEntries { [(it.names().sdlName()): it] }

        then:
        !result.hasErrors()
        result.diagnostics()*.code() == ['mapped-member', 'mapped-member']
        ir.models()*.names()*.sdlName() == ['Book']
        byName.Node.members()*.sdlName() == ['Book']
        !byName.Node.sealed()
        !byName.Result.sealed()
        byName.Result.members()*.sdlName() == ['Book']
    }

    def "an object type that interfaceTypes selects is an open interface and not a model, and a union with it is open too"() {
        when:
        def result = IrBuilder.fromSources([new SchemaSource('schema.graphqls', 'type Query { q: Int }\ntype Book { id: ID! }\nunion U = Book')],
                OPTIONS.toBuilder().interfaceTypes(['Book']).build())
        def ir = result.ir()

        then:
        result.diagnostics()*.code() == ['selected-member']
        ir.models().empty
        ir.abstractTypes()*.kind() == [AbstractType.Kind.OBJECT, AbstractType.Kind.UNION]
        !ir.abstractTypes()[0].sealed()
        ir.abstractTypes()[1].members()*.sdlName() == ['Book']
        !ir.abstractTypes()[1].sealed()
    }

    def "the default grouping is one contract for each root field, named by the template, in operation order"() {
        when:
        def ir = build('''
            schema { subscription: S  query: Q  mutation: M }
            type Q { first(id: ID!, limit: Int = 5): String  second: Int }
            type M { change: Boolean! }
            type S { ticks: Int! }
        ''', OPTIONS.toBuilder().kotlinSuspend(true).language(Language.KOTLIN).build()).ir()

        then:
        ir.contracts()*.names()*.qualifiedName() == ['com.example.resolver.FirstQResolver', 'com.example.resolver.SecondQResolver',
                                                     'com.example.resolver.ChangeMResolver', 'com.example.resolver.TicksSResolver']
        ir.contracts()*.operation() == [Operation.QUERY, Operation.QUERY, Operation.MUTATION, Operation.SUBSCRIPTION]
        ir.contracts()*.marker() == ['io.micronaut.graphql.tools.resolver.QueryResolver', 'io.micronaut.graphql.tools.resolver.QueryResolver',
                                     'io.micronaut.graphql.tools.resolver.MutationResolver',
                                     'io.micronaut.graphql.tools.resolver.SubscriptionResolver']
        ir.contracts()[0].methods()[0].arguments()*.name() == ['id', 'limit']
        ir.contracts()[0].methods()[0].arguments()[1].defaultValue() == '5'
        ir.contracts()[0].methods()[0].wrapper() == Wrapper.SUSPEND
        ir.contracts()[3].methods()[0].wrapper() == Wrapper.generic('org.reactivestreams.Publisher')
        ir.contracts()[0].methods()[0].coordinate().toString() == 'Q.first'
        ir.models().empty
    }

    def "rootContracts false generates no contract, and the roots are no models"() {
        when:
        def ir = build('type Query { a: Int }\ntype Other { b: Int }', OPTIONS.toBuilder().rootContracts(false).build()).ir()

        then:
        ir.contracts().empty
        ir.models()*.names()*.sdlName() == ['Other']
    }

    def "without a query type the roots are not implicit, and Mutation alone is no root"() {
        expect:
        build('type Mutation { a: Int }').ir().contracts().empty
        build('type Query { a: Int }\ntype Mutation { b: Int }').ir().contracts()*.operation() == [Operation.QUERY, Operation.MUTATION]
    }

    def "names carry the prefix, the suffix and the packages next to the SDL name"() {
        when:
        def ir = build('type Query { q: Int }\ntype Book { id: ID! }\nenum E { A }\ninput I { a: Int }',
                OPTIONS.toBuilder().typeNamePrefix('Gql').typeNameSuffix('Dto').modelPackage('com.other.m').build()).ir()

        then:
        ir.models()[0].names().sdlName() == 'Book'
        ir.models()[0].names().qualifiedName() == 'com.other.m.GqlBookDto'
        ir.enums()[0].names().simpleName() == 'GqlEDto'
        ir.inputs()[0].names().simpleName() == 'GqlIDto'
        ir.contracts()[0].names().qualifiedName() == 'com.example.resolver.QQueryResolver'
    }

    def "descriptions, comments, deprecation and directives are in the docs of the node"() {
        given:
        def schema = '''
            # about A
            # more
            type Query { q: A }

            """A **doc**."""
            type A {
              # about f
              #
              # about g
              g: Int @deprecated @tag(name: "x", n: 1, list: [1, "a"], o: {k: ENUM, t: true}, none: null)
              "own" h: Int @deprecated(reason: "Use g.")
            }
        '''

        when:
        def plain = build(schema).ir().models()[0]
        def comments = build(schema, OPTIONS.toBuilder().commentsAsDescriptions(true).build()).ir().models()[0]

        then:
        plain.docs().description() == 'A **doc**.'
        plain.fields()[0].docs() == new Docs(null, new Docs.Deprecation('No longer supported'))
        plain.fields()[0].directives()[0].name() == 'tag'
        plain.fields()[0].directives()[0].arguments() == [name: 'x', n: '1', list: '[1, "a"]', o: '{k: ENUM, t: true}', none: 'null']
        plain.fields()[0].directives()[0].arguments().keySet().toList() == ['name', 'n', 'list', 'o', 'none']
        plain.fields()[1].docs() == new Docs('own', new Docs.Deprecation('Use g.'))

        and: 'a comment is a description only when the option is on, and a blank line ends it'
        comments.docs().description() == 'A **doc**.'
        comments.fields()[0].docs().description() == ' about g'
        comments.fields()[1].docs().description() == 'own'
        build(schema, OPTIONS.toBuilder().commentsAsDescriptions(true).build()).ir().contracts().size() == 1
    }

    private static IrResult build(String schema, Options options = OPTIONS) {
        def result = IrBuilder.fromSources([new SchemaSource('schema.graphqls', schema)], options)
        assert result.diagnostics().empty: result.diagnostics()
        result
    }
}
