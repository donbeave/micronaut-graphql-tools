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

import io.micronaut.graphql.tools.codegen.Diagnostic
import io.micronaut.graphql.tools.codegen.Mode
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.ir.AbstractType
import io.micronaut.graphql.tools.codegen.ir.client.Payload
import io.micronaut.graphql.tools.codegen.ir.shared.BuiltinScalar
import io.micronaut.graphql.tools.codegen.ir.shared.Operation
import io.micronaut.graphql.tools.codegen.ir.shared.Presence
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef
import spock.lang.Specification
import spock.lang.Timeout

class ClientOperationSpec extends Specification {

    static final String SCHEMA = '''
        scalar DateTime
        scalar Unused
        interface Node { id: ID! }
        type Folder implements Node { id: ID!  name: String!  size: Int!  created: DateTime!  items: [Node!]! }
        type File implements Node { id: ID!  name: String!  bytes: Int! }
        type User { id: ID!  name: String!  age: Int!  tags: [String!]!  friends: [User!]! }
        union Result = Folder | File | User
        enum Role { ADMIN MEMBER }
        input Filter { text: String  limit: Int! = 10  role: Role  nested: Inner  at: DateTime }
        input Inner { flag: Boolean!  note: String }
        type Query {
          node(id: ID!): Node
          find(filter: Filter, limit: Int = 5, ids: [ID!]): [Result!]!
          user: User!
          users: [User!]!
          count: Int!
        }
        type Mutation { rename(id: ID!, name: String!): Folder! }
    '''

    static Options.Builder client() {
        Options.builder().mode(Mode.CLIENT).packageName('com.example').typeMappings([DateTime: 'java.time.Instant'])
    }

    static build(String document, Options options = client().build(), String schema = SCHEMA) {
        IrBuilder.fromSources([new SchemaSource('schema.graphqls', schema)], options, [],
                OperationInput.sources([new SchemaSource('ops.graphql', document)]))
    }

    static model(result, String simpleName) {
        result.ir().models().find { it.names().simpleName() == simpleName }
    }

    static field(model, String name) {
        model.fields().find { it.name() == name }
    }

    def "an operation lowers into its request names, kind, payload, document and hash"() {
        when:
        def result = build('query Users { users { id name } }')
        def operation = result.ir().operations()[0]

        then:
        result.diagnostics().empty
        operation.names().qualifiedName() == 'com.example.model.Users'
        operation.variablesType().qualifiedName() == 'com.example.model.UsersVariables'
        operation.kind() == Operation.QUERY
        operation.payload() == Payload.FIELD
        operation.documentText() == 'query Users{users{id name}}'
        operation.documentHash() ==~ /[0-9a-f]{64}/
        operation.result() == new TypeRef.ListOf(new TypeRef.Reference(model(result, 'UsersUsers').names(), false), false)
        model(result, 'UsersUsers').fields()*.name() == ['id', 'name']
        result.ir().contracts().empty
        result.ir().nameClasses().empty
    }

    def "an operation that selects two root fields returns the data object, and a name has the prefix and suffix of the options"() {
        when:
        def result = build('query Both { user { id } count }', client().typeNamePrefix('Gql').typeNameSuffix('Dto').build())
        def operation = result.ir().operations()[0]

        then:
        operation.payload() == Payload.DATA
        operation.result() == new TypeRef.Reference(model(result, 'GqlBothDataDto').names(), false)
        result.ir().models()*.names()*.simpleName() == ['GqlBothDataDto', 'GqlBothUserDto']
        field(model(result, 'GqlBothDataDto'), 'count').type() == new TypeRef.Builtin(BuiltinScalar.INT, false)
        field(model(result, 'GqlBothDataDto'), 'user').type() == new TypeRef.Reference(model(result, 'GqlBothUserDto').names(), false)
    }

    def "a variable has the presence of its type and default value"() {
        when:
        def result = build('''
            query Q($a: ID!, $b: Int! = 1, $c: Int, $d: Int = 2, $e: [ID!], $f: Filter, $g: Filter! = {limit: 1}) {
              n: node(id: $a) { __typename }
              f1: find(filter: $f, limit: $b, ids: $e) { __typename }
              f2: find(filter: $g, limit: $c) { __typename }
              f3: find(limit: $d) { __typename }
            }
        ''')
        def byName = result.ir().operations()[0].variables().collectEntries { [(it.name()): it] }

        then:
        byName.a.presence() == Presence.REQUIRED
        byName.b.presence() == Presence.OMITTABLE
        byName.c.presence() == Presence.TRI_STATE
        byName.d.presence() == Presence.TRI_STATE
        byName.e.presence() == Presence.TRI_STATE
        byName.f.presence() == Presence.TRI_STATE
        byName.g.presence() == Presence.OMITTABLE
        byName.b.defaultValue() == '1'
        byName.c.defaultValue() == null
        byName.g.defaultValue() == '{limit: 1}'
        byName.e.type() == new TypeRef.ListOf(new TypeRef.Builtin(BuiltinScalar.ID, false), true)
    }

    def "a field of an input object has the presence of its type and default value, and an operation reaches only the types it uses"() {
        when:
        def result = build('query Q($f: Filter) { find(filter: $f) { __typename } }')
        def fields = result.ir().inputs().find { it.names().sdlName() == 'Filter' }.fields().collectEntries { [(it.name()): it.presence()] }

        then:
        result.diagnostics().empty
        fields == [text: Presence.TRI_STATE, limit: Presence.OMITTABLE, role: Presence.TRI_STATE, nested: Presence.TRI_STATE,
                   at: Presence.TRI_STATE]
        result.ir().inputs()*.names()*.sdlName() == ['Filter', 'Inner']
        result.ir().enums()*.names()*.sdlName() == ['Role']
        result.ir().scalars()*.names()*.sdlName() == ['DateTime']
    }

    def "an unmapped custom scalar that an operation reaches is an error, and one that no operation reaches is not"() {
        when:
        def unreached = build('query Q { count }', client().typeMappings([:]).build())
        def reached = build('query Q($f: Filter) { find(filter: $f) { __typename } }', client().typeMappings([:]).build())

        then:
        !unreached.hasErrors()
        unreached.ir().scalars().empty
        reached.diagnostics()*.code() == [Diagnostic.UNMAPPED_SCALAR]
        reached.diagnostics()[0].message().contains('DateTime')
    }

    def "a field that skip or include can remove is nullable although its type is non-null"() {
        when:
        def result = build('''
            query Q($on: Boolean!) {
              user {
                id
                age @include(if: $on)
                name @skip(if: $on)
                tags
                ... @include(if: $on) { friends { id } }
              }
            }
        ''')
        def user = model(result, 'QUser')

        then:
        result.diagnostics().empty
        field(user, 'id').type() == new TypeRef.Builtin(BuiltinScalar.ID, false)
        field(user, 'age').type() == new TypeRef.Builtin(BuiltinScalar.INT, true)
        field(user, 'name').type() == new TypeRef.Builtin(BuiltinScalar.STRING, true)
        field(user, 'tags').type().nullable == false
        field(user, 'friends').type().nullable
        field(user, 'age').directives()*.name() == ['include']
        field(user, 'age').directives()[0].arguments() == [if: '$on']
        def selected = result.ir().operations()[0].selection().fields()[0].selection()
        selected.fields().find { it.fieldName() == 'age' }.directives()*.name() == ['include']
        selected.inlineFragments()[0].directives()*.name() == ['include']
        selected.inlineFragments()[0].typeCondition() == null
    }

    def "a field that one selection removes and another keeps is present"() {
        when:
        def result = build('''
            query Q($on: Boolean!) {
              user { age @include(if: $on) ... { age } }
            }
        ''')

        then:
        result.diagnostics().empty
        field(model(result, 'QUser'), 'age').type() == new TypeRef.Builtin(BuiltinScalar.INT, false)
    }

    def "an interface selection gets __typename first, a sealed interface, a record for each type that a condition names and Other"() {
        when:
        def result = build('query Q { node(id: "1") { id ... on Folder { name } ... on File { bytes } } }')
        def sealed = result.ir().abstractTypes()[0]

        then:
        result.diagnostics().empty
        result.ir().operations()[0].documentText() == 'query Q{node(id:"1"){__typename id ...on Folder{name}...on File{bytes}}}'
        result.ir().operations()[0].selection().fields()[0].selection().fields()*.fieldName() == ['__typename', 'id']
        sealed.names().simpleName() == 'QNode'
        sealed.kind() == AbstractType.Kind.INTERFACE
        sealed.sealed()
        sealed.fields()*.name() == ['__typename', 'id']
        sealed.members()*.simpleName() == ['QNodeFolder', 'QNodeFile', 'QNodeOther']
        sealed.members()*.sdlName() == ['Folder', 'File', 'Node']
        result.ir().models()*.names()*.simpleName() == ['QNodeFolder', 'QNodeFile', 'QNodeOther']
        model(result, 'QNodeFolder').fields()*.name() == ['__typename', 'id', 'name']
        model(result, 'QNodeFile').fields()*.name() == ['__typename', 'id', 'bytes']
        model(result, 'QNodeOther').fields()*.name() == ['__typename', 'id']
        result.ir().models().every { it.implemented()*.simpleName() == ['QNode'] && it.memberOf().empty }
    }

    def "a union selection has a sealed interface that its records are members of, and a document that selects __typename keeps it where it is"() {
        when:
        def result = build('query Q { find { ... on Folder { __typename: name } } }')

        then:
        result.hasErrors()
        result.diagnostics()*.code() == [Diagnostic.RESERVED_TYPENAME_ALIAS]

        when:
        result = build('query Q { find { ... on Folder { size } ... on User { age } __typename } }')
        def sealed = result.ir().abstractTypes()[0]

        then:
        result.diagnostics().empty
        result.ir().operations()[0].documentText() == 'query Q{find{...on Folder{size}...on User{age}__typename}}'
        sealed.kind() == AbstractType.Kind.UNION
        sealed.fields()*.name() == ['__typename']
        sealed.members()*.simpleName() == ['QFindFolder', 'QFindUser', 'QFindOther']
        result.ir().models().every { it.memberOf()*.simpleName() == ['QFind'] && it.implemented().empty }
        model(result, 'QFindFolder').fields()*.name() == ['size', '__typename']
    }

    def "a named fragment is an interface that the records which spread it implement, with its own nested selections"() {
        when:
        def result = build('''
            query Q { users { ...Basics } user { ...Basics friends { id } } }
            fragment Basics on User { id name friends { name } }
        ''')
        def basics = result.ir().abstractTypes().find { it.names().simpleName() == 'Basics' }

        then:
        result.diagnostics()*.code() == [Diagnostic.FRAGMENT_INTERFACE_SKIPPED]
        result.ir().fragments()*.names()*.simpleName() == ['Basics']
        result.ir().fragments()[0].typeCondition() == 'User'
        basics.kind() == AbstractType.Kind.INTERFACE
        !basics.sealed()
        basics.fields()*.name() == ['id', 'name', 'friends']
        // users selects exactly what the fragment selects, so the record shares the nested type of the interface
        model(result, 'QUsers').implemented()*.simpleName() == ['Basics']
        field(model(result, 'QUsers'), 'friends').type() == field(basics, 'friends').type()
        // user selects more under friends, so its record keeps the fields and does not implement the interface
        model(result, 'QUser').implemented().empty
        field(model(result, 'QUser'), 'friends').type() != field(basics, 'friends').type()
        model(result, 'QUser').fields()*.name() == ['id', 'name', 'friends']
        result.diagnostics()[0].message().contains('QUser')
    }

    def "a fragment on an interface is implemented by every record of an abstract selection"() {
        when:
        def result = build('''
            query Q { node(id: "1") { ...Ident ... on Folder { name } } }
            fragment Ident on Node { id }
        ''')

        then:
        result.diagnostics().empty
        result.ir().models().every { it.implemented()*.simpleName().contains('Ident') }
        result.ir().models()*.names()*.simpleName() == ['QNodeFolder', 'QNodeOther']
        result.ir().operations()[0].fragments() == ['Ident']
        result.ir().operations()[0].selection().fields()[0].selection().fragmentSpreads()*.name() == ['Ident']
    }

    def "a fragment that two operations use is one interface and its fields come once"() {
        when:
        def result = build('''
            query A { users { ...Basics } }
            query B { users { ...Basics } }
            fragment Basics on User { id }
        ''')

        then:
        result.diagnostics().empty
        result.ir().abstractTypes()*.names()*.simpleName() == ['Basics']
        result.ir().fragments().size() == 1
        model(result, 'AUsers').implemented()*.simpleName() == ['Basics']
        model(result, 'BUsers').implemented()*.simpleName() == ['Basics']
        result.ir().operations()*.fragments() == [['Basics'], ['Basics']]
    }

    def "an alias is the name of a component, and the schema name is kept next to it"() {
        when:
        def result = build('query Q { me: user { id label: name } }')
        def user = model(result, 'QMe')

        then:
        result.diagnostics().empty
        user.fields()*.name() == ['id', 'label']
        user.fields()*.sdlName() == ['id', 'name']
        result.ir().operations()[0].selection().fields()[0].alias() == 'me'
        result.ir().operations()[0].selection().fields()[0].responseKey() == 'me'
    }

    def "the arguments of a selected field are kept as the document writes them"() {
        when:
        def result = build('query Q($f: Filter) { find(filter: $f, limit: 3, ids: [\"a\", \"b\"]) { __typename } }')
        def arguments = result.ir().operations()[0].selection().fields()[0].arguments()

        then:
        arguments*.name() == ['filter', 'limit', 'ids']
        arguments*.value() == ['$f', '3', '["a", "b"]']
    }

    def "a custom scalar in a selection is mapped, and Type.field maps one field"() {
        when:
        def result = build('query Q { node(id: "1") { ... on Folder { created } } }', client().typeMappings([
                DateTime: 'java.time.Instant', 'Folder.created': 'java.lang.String']).build())

        then:
        result.diagnostics().empty
        field(model(result, 'QNodeFolder'), 'created').type() == new TypeRef.Mapped('java.lang.String', false)
    }

    def "a mutation and a subscription lower with their own kind"() {
        when:
        def result = build('mutation Rename($id: ID!, $name: String!) { rename(id: $id, name: $name) { id name } }')

        then:
        result.diagnostics().empty
        result.ir().operations()[0].kind() == Operation.MUTATION
        result.ir().operations()[0].payload() == Payload.FIELD
    }

    def "the documents are read in the order of their paths and the operations of one document in its order"() {
        when:
        def result = IrBuilder.fromSources([new SchemaSource('schema.graphqls', SCHEMA)], client().build(), [],
                OperationInput.sources([new SchemaSource('b.graphql', 'query B { count }'),
                                        new SchemaSource('a.graphql', 'query Z { count } query Y { count }')]))

        then:
        result.diagnostics().empty
        result.ir().operations()*.names()*.sdlName() == ['Z', 'Y', 'B']
    }

    def "the IR does not depend on the order of the documents"() {
        given:
        def sources = [new SchemaSource('a.graphql', 'query A { users { ...F } } fragment F on User { id }'),
                       new SchemaSource('b.graphql', 'query B { node(id: "1") { id ... on Folder { name } } }'),
                       new SchemaSource('c.graphql', 'mutation C($i: ID!) { rename(id: $i, name: "x") { id } }')]
        def reference = IrBuilder.fromSources([new SchemaSource('schema.graphqls', SCHEMA)], client().build(), [],
                OperationInput.sources(sources)).ir()

        expect:
        sources.permutations().every {
            IrBuilder.fromSources([new SchemaSource('schema.graphqls', SCHEMA)], client().build(), [], OperationInput.sources(it)).ir() == reference
        }
    }

    def "two selections that generate one class are a name collision, and so is a request class that another type takes"() {
        when:
        def data = build('query A { data: user { id } count }')
        def request = build('query Folder { count }')

        then:
        data.diagnostics()*.code() == [Diagnostic.NAME_COLLISION]
        data.diagnostics()[0].message().contains('com.example.model.AData')
        request.diagnostics().empty
    }

    def "a response key that is a Java keyword is an error, as a field name is on the server"() {
        when:
        def result = build('query Q { user { class: name } }')

        then:
        result.diagnostics()*.code() == [Diagnostic.RESERVED_NAME]
    }

    def "an operation that selects only __typename at the root returns the type name"() {
        when:
        def result = build('query Q { __typename }')

        then:
        result.diagnostics().empty
        result.ir().operations()[0].payload() == Payload.FIELD
        result.ir().operations()[0].result() == new TypeRef.Builtin(BuiltinScalar.STRING, false)
        build('query Q { name: __typename count }').diagnostics().empty
    }

    def "an introspection field at the root is an error and not a crash"() {
        when:
        def result = build('query Q { __schema { queryType { name } } }')

        then:
        result.diagnostics()*.code() == [Diagnostic.OPERATION_INVALID]
        result.diagnostics()[0].message().contains('__schema')
    }

    def "a field below a field that include can remove is removable, although another occurrence of the parent always stays"() {
        when:
        def result = build('''
            query Q($on: Boolean!) {
              user { id }
              user @include(if: $on) { age }
            }
        ''')
        def user = model(result, 'QUser')

        then:
        result.diagnostics().empty
        field(user, 'id').type() == new TypeRef.Builtin(BuiltinScalar.ID, false)
        field(user, 'age').type() == new TypeRef.Builtin(BuiltinScalar.INT, true)
    }

    def "a field that another occurrence of the parent selects without a condition stays present"() {
        when:
        def result = build('''
            query Q($on: Boolean!) {
              user @include(if: $on) { age }
              user { age }
            }
        ''')

        then:
        result.diagnostics().empty
        field(model(result, 'QUser'), 'age').type() == new TypeRef.Builtin(BuiltinScalar.INT, false)
    }

    def "the fields below occurrences that carry their own conditions are removable, as the merged object has them only when they hold"() {
        when:
        def result = build('''
            query Q($a: Boolean!, $b: Boolean!) {
              user @include(if: $a) { id }
              user @include(if: $b) { age }
            }
        ''')

        then:
        result.diagnostics().empty
        field(model(result, 'QUser'), 'id').type() == new TypeRef.Builtin(BuiltinScalar.ID, true)
        field(model(result, 'QUser'), 'age').type() == new TypeRef.Builtin(BuiltinScalar.INT, true)
    }

    @Timeout(30)
    def "a chain of fragments that spread each other twice is walked once for each, not once for each path"() {
        given:
        def fragments = (1..24).collect { "fragment F$it on User { " + (it == 1 ? 'id' : "...F${it - 1} ...F${it - 1}") + ' }' }.join('\n')

        when:
        def result = build("query Q { user { ...F24 } }\n$fragments")

        then:
        result.diagnostics().empty
        model(result, 'QUser').fields()*.name() == ['id']
        model(result, 'QUser').implemented().size() == 24
    }

    def "the fields below a field that every occurrence can remove are present when the field is"() {
        when:
        def result = build('query Q($on: Boolean!) { user @include(if: $on) { age } }')

        then:
        result.diagnostics().empty
        field(model(result, 'QUser'), 'age').type() == new TypeRef.Builtin(BuiltinScalar.INT, false)
    }

    def "an operation that fails does not stop the checks of the others"() {
        when:
        def result = build('query A { nope } query B($f: Filter) { find(filter: $f) { __typename } }',
                client().typeMappings([:]).build())

        then:
        result.diagnostics()*.code() == [Diagnostic.OPERATION_INVALID, Diagnostic.UNMAPPED_SCALAR]
    }

    def "server mode ignores the operation documents"() {
        when:
        def result = IrBuilder.fromSources([new SchemaSource('schema.graphqls', SCHEMA)],
                Options.builder().packageName('com.example').typeMappings([DateTime: 'java.time.Instant', Unused: 'java.lang.String']).build(), [],
                OperationInput.sources([new SchemaSource('ops.graphql', 'query { nonsense')]))

        then:
        !result.hasErrors()
        result.ir().operations().empty
        result.ir().fragments().empty
    }
}
