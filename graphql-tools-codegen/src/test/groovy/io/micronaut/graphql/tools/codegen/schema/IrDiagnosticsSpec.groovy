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
import io.micronaut.graphql.tools.codegen.Language
import io.micronaut.graphql.tools.codegen.NameConstants
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.Origin
import spock.lang.Specification
import spock.lang.Unroll

class IrDiagnosticsSpec extends Specification {

    static final Options OPTIONS = Options.builder().packageName('com.example').build()

    def "an unmapped custom scalar is an error at the scalar, never a silent String"() {
        when:
        def result = build('type Query { when: Date }\n\nscalar Date')

        then:
        result.diagnostics().size() == 1
        with(result.diagnostics()[0]) {
            code() == Diagnostic.UNMAPPED_SCALAR
            severity() == Diagnostic.Severity.ERROR
            origin() == new Origin('schema.graphqls', 3, 1)
            message() == "The custom scalar 'Date' (schema.graphqls:3:1) has no Java class. Map it with the option typeMappings.Date, " +
                    "for example typeMappings.Date=java.time.LocalDate. The generator never falls back to String."
        }
        result.ir().models().empty
        result.hasErrors()
    }

    def "a custom scalar with a mapping is a mapped reference, and a Type.field mapping wins for that field"() {
        when:
        def result = build('type Query { a: Date  b: [Date!] }\nscalar Date',
                OPTIONS.toBuilder().typeMappings(['Date': 'java.time.LocalDate', 'Query.b': 'java.time.Instant']).build())

        then:
        result.diagnostics().empty
        result.ir().scalars()*.className() == ['java.time.LocalDate']
        result.ir().contracts()*.methods().flatten()*.returns() == [
                new io.micronaut.graphql.tools.codegen.ir.shared.TypeRef.Mapped('java.time.LocalDate', true),
                new io.micronaut.graphql.tools.codegen.ir.shared.TypeRef.ListOf(
                        new io.micronaut.graphql.tools.codegen.ir.shared.TypeRef.Mapped('java.time.Instant', false), true)]
    }

    @Unroll
    def "a Java keyword as #what is an error with the element and its location"() {
        when:
        def result = build(schema)

        then:
        result.diagnostics().size() == 1
        with(result.diagnostics()[0]) {
            code() == Diagnostic.RESERVED_NAME
            origin() == new Origin('schema.graphqls', line, column)
            message() == text
        }

        where:
        what                | schema                                                        | line | column | text
        'a field name'      | 'type Query { a: A }\ntype A {\n  class: String\n}'            | 3    | 3      | "The field 'A.class' (schema.graphqls:3:3) cannot be generated: class is a Java keyword. Rename it in the schema; the generator has no renaming option yet."
        'an input field'    | 'type Query { a(i: I): String }\ninput I { new: Int }'         | 2    | 11     | "The field 'I.new' (schema.graphqls:2:11) cannot be generated: new is a Java keyword. Rename it in the schema; the generator has no renaming option yet."
        'an enum value'     | 'type Query { e: E }\nenum E {\n  A\n  default\n}'            | 4    | 3      | "The enum value 'E.default' (schema.graphqls:4:3) cannot be generated: default is a Java keyword. Rename it in the schema; the generator has no renaming option yet."
        'an argument'       | 'type Query {\n  f(int: Int): String\n}'                        | 2    | 5      | "The argument 'Query.f(int)' (schema.graphqls:2:5) cannot be generated: int is a Java keyword. Rename it in the schema; the generator has no renaming option yet."
        'a root field'      | 'type Query {\n  package: String\n}'                             | 2    | 3      | "The field 'Query.package' (schema.graphqls:2:3) cannot be generated: package is a Java keyword. Rename it in the schema; the generator has no renaming option yet."
    }

    @Unroll
    def "a #language keyword is an error only for that language"() {
        given:
        def schema = 'type Query { q: String }\ntype A {\n  ' + word + ': String\n}'

        when:
        def result = build(schema, OPTIONS.toBuilder().language(language).build())

        then:
        result.diagnostics()*.code() == codes

        where:
        language        | word  | codes
        Language.KOTLIN | 'fun' | [Diagnostic.RESERVED_NAME]
        Language.JAVA   | 'fun' | []
        Language.GROOVY | 'def' | [Diagnostic.RESERVED_NAME]
        Language.JAVA   | 'def' | []
    }

    @Unroll
    def "the field #name clashes with java.lang.Object and names its location"() {
        when:
        def result = build("type Query { q: A }\ntype A {\n  ${name}: Int\n}\ninput B { ${name}: Int }")

        then:
        result.diagnostics()*.code() == [Diagnostic.OBJECT_METHOD_NAME, Diagnostic.OBJECT_METHOD_NAME]
        result.diagnostics()[0].origin() == new Origin('schema.graphqls', 3, 3)
        result.diagnostics()[0].message() == "The field 'A.${name}' (schema.graphqls:3:3) clashes with a method of java.lang.Object: " +
                "a record component cannot be named ${name}. Rename it in the schema; the generator has no renaming option yet."
        result.diagnostics()[1].origin() == new Origin('schema.graphqls', 5, 11)

        where:
        name << ['clone', 'finalize', 'getClass', 'hashCode', 'notify', 'notifyAll', 'toString', 'wait']
    }

    def "a Java keyword in a type name that a prefix or suffix cannot fix is an error"() {
        when:
        def result = build('type Query { q: enum }\ntype enum { a: Int }')

        then:
        result.diagnostics()*.code() == [Diagnostic.RESERVED_NAME]
        result.diagnostics()[0].origin() == new Origin('schema.graphqls', 2, 1)

        and: 'a suffix fixes it'
        build('type Query { q: enum }\ntype enum { a: Int }', OPTIONS.toBuilder().typeNameSuffix('Type').build()).diagnostics().empty
    }

    def "two schema names that differ only in case collide, and the diagnostic names both elements"() {
        when:
        def result = IrBuilder.fromSources([
                new SchemaSource('a.graphqls', 'type Query { f: Foo  g: foo }\ntype Foo { x: Int }'),
                new SchemaSource('b.graphqls', 'type foo { y: Int }')], OPTIONS)

        then: 'the name constants classes collide too'
        result.diagnostics().size() == 2
        result.diagnostics()[1].message().contains("the class of the name constants of the type 'Foo' (a.graphqls:2:1) generates com.example.names.FooNames and")
        with(result.diagnostics()[0]) {
            code() == Diagnostic.NAME_COLLISION
            origin() == new Origin('b.graphqls', 1, 1)
            message() == "Two schema elements generate classes whose names differ only in case, so they overwrite each other on a " +
                    "case-insensitive file system: the object type 'Foo' (a.graphqls:2:1) generates com.example.model.Foo and the " +
                    "object type 'foo' (b.graphqls:1:1) generates com.example.model.foo. Rename one of them in the schema, or set " +
                    "typeNamePrefix, typeNameSuffix or a package option."
        }
    }

    def "two contracts that the template gives one name are a collision of the same class"() {
        when:
        def result = build('type Query { a: Int  b: Int }', OPTIONS.toBuilder().contractNameTemplate('Same').build())

        then:
        result.diagnostics()*.code() == [Diagnostic.NAME_COLLISION]
        result.diagnostics()[0].message().contains("the resolver contract of the field 'Query.a' (schema.graphqls:1:14) and the resolver " +
                "contract of the field 'Query.b' (schema.graphqls:1:22)")
        result.diagnostics()[0].message().startsWith('Two schema elements generate the class com.example.resolver.Same:')
    }

    def "a schema type named like a generated helper class is a collision when the packages are shared"() {
        given:
        def shared = OPTIONS.toBuilder().modelPackage('com.example.all').namesPackage('com.example.all').build()
        def schema = 'type Query { a: GraphQLTypeNames  b: BookNames }\ntype GraphQLTypeNames { x: Int }\ntype Book { x: Int }\ntype BookNames { x: Int }'

        when:
        def result = build(schema, shared)

        then:
        result.diagnostics()*.code() == [Diagnostic.NAME_COLLISION, Diagnostic.NAME_COLLISION]
        result.diagnostics()[0].message() == "Two schema elements generate the class com.example.all.GraphQLTypeNames: the object type " +
                "'GraphQLTypeNames' (schema.graphqls:2:1) and the class of the type name constants. Rename one of them in the " +
                "schema, or set typeNamePrefix, typeNameSuffix or a package option."
        result.diagnostics()[1].message().contains("the object type 'BookNames' (schema.graphqls:4:1) and the class of the name constants of the type 'Book' (schema.graphqls:3:1)")

        and: 'the default packages, or no constants, have no collision'
        build(schema).diagnostics().empty
        build(schema, shared.toBuilder().nameConstants(NameConstants.NONE).build()).diagnostics().empty
        build(schema, shared.toBuilder().nameConstants(NameConstants.TYPES).build()).diagnostics()*.code() == [Diagnostic.NAME_COLLISION]
    }

    def "a type that nothing defines is an error at the field that uses it"() {
        when:
        def result = build('type Query {\n  a(arg: Gone): Missing\n}')

        then:
        result.diagnostics()*.code() == [Diagnostic.UNKNOWN_TYPE, Diagnostic.UNKNOWN_TYPE]
        result.diagnostics()[0].message() == "The type 'Gone' of the argument 'Query.a(arg)' (schema.graphqls:2:5) is not defined in the schema files and has no entry in the option typeMappings."
        result.diagnostics()[1].message() == "The type 'Missing' of the field 'Query.a' (schema.graphqls:2:3) is not defined in the schema files and has no entry in the option typeMappings."
    }

    def "a root type used as a field type is reported, and a mapping lifts it"() {
        given:
        def schema = 'type Query { me: Query }'

        expect:
        build(schema).diagnostics()*.code() == [Diagnostic.ROOT_TYPE_REFERENCE]
        build(schema, OPTIONS.toBuilder().typeMappings(['Query': 'com.example.Root']).build()).diagnostics().empty
    }

    def "an entry of typeMappings or interfaceTypes that names nothing is a warning that names the option"() {
        when:
        def result = build('type Query { a: String }\nenum E { A }',
                OPTIONS.toBuilder().typeMappings(['Nope': 'a.B', 'Query.nope': 'a.C', 'Query.a': 'a.D']).interfaceTypes(['Nope', 'E']).build())

        then:
        result.diagnostics()*.code() == [Diagnostic.UNUSED_TYPE_MAPPING, Diagnostic.UNUSED_TYPE_MAPPING,
                                         Diagnostic.UNUSED_INTERFACE_TYPE, Diagnostic.UNUSED_INTERFACE_TYPE]
        result.diagnostics()*.optionName() == ['typeMappings.Nope', 'typeMappings.Query.nope', 'interfaceTypes.0', 'interfaceTypes.1']
        result.diagnostics().every { it.severity() == Diagnostic.Severity.WARNING }
        !result.hasErrors()
        result.ir().contracts().size() == 1
    }

    def "a mapping that gives a type that no file defines is used, not unused"() {
        expect:
        build('type Query { when: Long }', OPTIONS.toBuilder().typeMappings(['Long': 'java.lang.Long']).build()).diagnostics().empty
        build('type Query { when: [Long] }', OPTIONS.toBuilder().typeMappings(['Query.when': 'java.lang.Long']).build()).diagnostics().empty
    }

    def "a root operation that names a type that is not an object is an error"() {
        when:
        def result = build('schema { query: Q }\nenum Q { A }')

        then:
        result.diagnostics()*.code() == [Diagnostic.UNKNOWN_TYPE]
        result.diagnostics()[0].message() == "The query root type 'Q' at schema.graphqls:1:10 is not an object type of the schema files."
    }

    def "every problem is reported in one run, in schema order"() {
        when:
        def result = build('scalar A\nscalar B\ntype Query { class: String  hashCode: Int }')

        then:
        result.diagnostics()*.code() == [Diagnostic.UNMAPPED_SCALAR, Diagnostic.UNMAPPED_SCALAR, Diagnostic.RESERVED_NAME,
                                         Diagnostic.OBJECT_METHOD_NAME]
        result.diagnostics()*.origin()*.line() == [1, 2, 3, 3]
    }

    def "invalid options are reported with the diagnostics of the schema, and the lowering waits"() {
        when:
        def result = build('type Query { a: Unknown }', Options.builder().build())

        then:
        result.diagnostics()*.code() == [Diagnostic.MISSING_OPTION]
        result.ir().contracts().empty
    }

    private static IrResult build(String schema, Options options = OPTIONS) {
        IrBuilder.fromSources([new SchemaSource('schema.graphqls', schema)], options)
    }
}
