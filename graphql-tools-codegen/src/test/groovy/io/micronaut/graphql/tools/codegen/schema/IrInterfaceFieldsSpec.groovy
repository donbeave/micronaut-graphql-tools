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
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.Origin
import io.micronaut.graphql.tools.codegen.ir.IrSnapshot
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The rules for the fields that implement an interface field: a list that an object narrows keeps the element type of the
 * interface (Java generics are invariant), and a non-null {@code Int}, {@code Float} or {@code Boolean} that implements a nullable
 * one stays boxed (a primitive return cannot override a boxed one).
 */
class IrInterfaceFieldsSpec extends Specification {

    static final Options OPTIONS = Options.builder().packageName('com.example').build()

    static final String NODES = '''
type Query { node: Node }
interface Node { id: ID! children: [Node!]! }
type Folder implements Node { id: ID! children: [Folder!]! }
'''

    def "a list that an object narrows keeps the element type of the interface, and a warning says so"() {
        when:
        def result = build(NODES)

        then:
        !result.hasErrors()
        text(field(result, 'Folder', 'children').type()) == '[Node!]!'
        text(field(result, 'Node', 'children').type()) == '[Node!]!'
        result.diagnostics().size() == 1
        with(result.diagnostics()[0]) {
            code() == Diagnostic.NARROWED_LIST_FIELD
            severity() == Diagnostic.Severity.WARNING
            origin() == new Origin('schema.graphqls', 4, 39)
            message() == "The field 'Folder.children' (schema.graphqls:4:39) narrows the list element type 'Node' of the field " +
                    "'Node.children' to 'Folder'. Java generics are invariant, so the generated type keeps the list of the interface, " +
                    "with the element type 'Node'; the schema still returns the narrower objects."
        }
    }

    @Unroll
    def "the nullability of the object stays when the list is widened: #own against #declared"() {
        when:
        def result = build("type Query { n: Node }\ninterface Node { items: $declared }\ntype Folder implements Node { items: $own }")

        then:
        !result.hasErrors()
        text(field(result, 'Folder', 'items').type()) == expected
        result.diagnostics()*.code() == [Diagnostic.NARROWED_LIST_FIELD]

        where:
        declared      | own            | expected
        '[Node!]!'    | '[Folder!]!'   | '[Node!]!'
        '[Node]'      | '[Folder!]!'   | '[Node!]!'
        '[Node!]!'    | '[Folder]'     | '[Node]'
        '[Node]'      | '[Folder!]'    | '[Node!]'
        '[[Node!]!]!' | '[[Folder!]!]!' | '[[Node!]!]!'
        '[[Node]]'    | '[[Folder!]!]!' | '[[Node!]!]!'
    }

    def "a list with the same element type, and a single value that is narrowed, are left alone"() {
        when:
        def result = build('''
type Query { n: Node }
interface Node { id: ID! parent: Node tags: [String!]! siblings: [Node!]! }
type Folder implements Node { id: ID! parent: Folder tags: [String!]! siblings: [Node!]! }
''')

        then:
        result.diagnostics().empty
        text(field(result, 'Folder', 'parent').type()) == 'Folder'
        text(field(result, 'Folder', 'siblings').type()) == '[Node!]!'
    }

    def "an interface that narrows the list of an interface it implements is widened, and so is an object that implements both"() {
        when:
        def result = build('''
type Query { a: A }
interface A { items: [A!]! }
interface B implements A { items: [B!]! }
type C implements A & B { items: [C!]! }
''')

        then:
        !result.hasErrors()
        text(field(result, 'B', 'items').type()) == '[A!]!'
        text(field(result, 'C', 'items').type()) == '[A!]!'
        result.diagnostics()*.code() == [Diagnostic.NARROWED_LIST_FIELD, Diagnostic.NARROWED_LIST_FIELD]
    }

    def "an object type that interfaceTypes selects is widened like a model"() {
        when:
        def result = build(NODES, OPTIONS.toBuilder().interfaceTypes(['Folder']).build())

        then:
        !result.hasErrors()
        result.ir().models().empty
        text(result.ir().abstractTypes().find { it.names().sdlName() == 'Folder' }.fields()[1].type()) == '[Node!]!'
    }

    def "two interfaces that declare a list with different element types are an error at the field that implements both"() {
        when:
        def result = build('''
type Query { a: A }
interface A { items: [A!]! }
interface B { items: [B!]! }
type C implements A & B { items: [C!]! }
''')

        then:
        result.hasErrors()
        result.diagnostics()*.code() == [Diagnostic.INTERFACE_FIELD_CONFLICT]
        with(result.diagnostics()[0]) {
            origin() == new Origin('schema.graphqls', 5, 27)
            message() == "The field 'C.items' (schema.graphqls:5:27) implements the field 'A.items' and the field 'B.items', which " +
                    "differ in a list element type or in a primitive against a nullable scalar. No single Java type implements both. " +
                    "Give the two interface fields the same list element type and nullability."
        }
    }

    @Unroll
    def "a non-null #scalar that implements a nullable one stays boxed"() {
        when:
        def result = build("type Query { n: Node }\ninterface Node { value: $scalar }\ntype Item implements Node { value: $scalar! }")

        then:
        result.diagnostics().empty
        field(result, 'Item', 'value').boxed()
        text(field(result, 'Item', 'value').type()) == "$scalar!"
        !field(result, 'Node', 'value').boxed()

        where:
        scalar << ['Int', 'Float', 'Boolean']
    }

    def "a scalar is boxed only when an implemented interface declares it nullable"() {
        when:
        def result = build('''
type Query { n: Node }
interface Node { a: Int b: Int! c: Int d: String e: Int! }
type Item implements Node { a: Int b: Int! c: Int! d: String! e: Int! }
type Plain { a: Int! }
''')

        then:
        result.diagnostics().empty
        ['a', 'b', 'c', 'd', 'e'].collect { field(result, 'Item', it).boxed() } == [false, false, true, false, false]
        !field(result, 'Plain', 'a').boxed()
    }

    def "the boxed representation is passed down an interface chain"() {
        when:
        def result = build('''
type Query { a: A }
interface A { n: Int }
interface B implements A { n: Int! }
type C implements A & B { n: Int! }
''')

        then:
        result.diagnostics().empty
        field(result, 'B', 'n').boxed()
        field(result, 'C', 'n').boxed()
        !field(result, 'A', 'n').boxed()
    }

    def "a primitive and a boxed requirement from two interfaces are an error"() {
        when:
        def result = build('''
type Query { a: A }
interface A { n: Int }
interface B { n: Int! }
type C implements A & B { n: Int! }
''')

        then:
        result.diagnostics()*.code() == [Diagnostic.INTERFACE_FIELD_CONFLICT]
    }

    def "the IR text marks a boxed field and prints no mark for any other"() {
        when:
        def snapshot = IrSnapshot.print(build(
                'type Query { n: Node }\ninterface Node { value: Int }\ntype Item implements Node { value: Int! other: Int! }').ir())

        then:
        snapshot.contains('  field value: Int! boxed @ schema.graphqls:3:29\n')
        snapshot.contains('  field other: Int! @ schema.graphqls:3:41\n')
    }

    private static Object field(IrResult result, String type, String name) {
        def ir = result.ir()
        def owner = ir.models().find { it.names().sdlName() == type } ?: ir.abstractTypes().find { it.names().sdlName() == type }
        owner.fields().find { it.sdlName() == name }
    }

    private static String text(TypeRef type) {
        String suffix = type.nullable ? '' : '!'
        if (type instanceof TypeRef.ListOf) {
            return "[${text(type.element())}]$suffix"
        }
        String name = type instanceof TypeRef.Reference ? type.target().sdlName() :
                type instanceof TypeRef.Mapped ? type.className() : type.scalar().name().toLowerCase().capitalize().replace('Id', 'ID')
        name + suffix
    }

    private static IrResult build(String schema, Options options = OPTIONS) {
        IrBuilder.fromSources([new SchemaSource('schema.graphqls', schema)], options)
    }
}
