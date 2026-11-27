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
import io.micronaut.graphql.tools.codegen.GraphQLCodeGenerator
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.golden.GoldenFiles
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TypeMappingsSpec extends Specification {

    static final Options OPTIONS = Options.builder().packageName('com.example').build()

    @TempDir Path dir

    def "the built-in scalars of the library map to their classes without an entry"() {
        when:
        def result = build('''type Query { a: Long  b: Short!  c: BigDecimal  d: BigInteger  e: JSON  f: Upload }
scalar Long
scalar Short
scalar BigDecimal
scalar BigInteger
scalar JSON
scalar Upload''')

        then:
        result.diagnostics().empty
        result.ir().scalars()*.className() == ['java.lang.Long', 'java.lang.Short', 'java.math.BigDecimal', 'java.math.BigInteger',
                                               'java.lang.Object', 'io.micronaut.http.multipart.CompletedFileUpload']
        result.ir().contracts()*.methods().flatten()*.returns() == [
                new TypeRef.Mapped('java.lang.Long', true), new TypeRef.Mapped('java.lang.Short', false),
                new TypeRef.Mapped('java.math.BigDecimal', true), new TypeRef.Mapped('java.math.BigInteger', true),
                new TypeRef.Mapped('java.lang.Object', true), new TypeRef.Mapped('io.micronaut.http.multipart.CompletedFileUpload', true)]
    }

    def "a preset applies to a scalar that the schema does not declare, and never to another kind of type"() {
        when:
        def result = build('type Query { a: Long  b: Currency }\ntype Currency { code: String }')

        then:
        result.diagnostics().empty
        result.ir().models()*.names()*.simpleName() == ['Currency']
        result.ir().contracts()*.methods().flatten()*.returns()[0] == new TypeRef.Mapped('java.lang.Long', true)
        result.ir().contracts()*.methods().flatten()*.returns()[1] instanceof TypeRef.Reference
    }

    def "an entry of typeMappings wins over a preset"() {
        when:
        def result = build('type Query { a: Long }\nscalar Long', OPTIONS.toBuilder().typeMappings([Long: 'java.math.BigInteger']).build())

        then:
        result.diagnostics().empty
        result.ir().scalars()*.className() == ['java.math.BigInteger']
    }

    def "a custom scalar that has no mapping and no preset is an error that names it"() {
        when:
        def result = build('type Query { a: Money }\nscalar Money')

        then:
        result.hasErrors()
        result.diagnostics()*.code() == [Diagnostic.UNMAPPED_SCALAR]
        result.diagnostics()[0].message().contains("'Money'")
    }

    def "a type-level mapping of an object, an enum and an input generates nothing and is used as the class"() {
        when:
        def result = build('''type Query { p(i: In): Pet  c: Color }
type Pet { name: String }
enum Color { RED }
input In { a: Int }''', OPTIONS.toBuilder().typeMappings([Pet: 'org.ext.Pet', Color: 'org.ext.Color', In: 'org.ext.In']).build())

        then:
        result.diagnostics().empty
        result.ir().models().empty
        result.ir().enums().empty
        result.ir().inputs().empty
        result.ir().contracts()[0].methods()[0].returns() == new TypeRef.Mapped('org.ext.Pet', true)
        result.ir().contracts()[0].methods()[0].arguments()[0].type() == new TypeRef.Mapped('org.ext.In', true)
    }

    def "a field-level mapping of a scalar field applies to that field only"() {
        when:
        def result = build('type Query { a: Pet }\ntype Pet { born: Long  seen: Long }',
                OPTIONS.toBuilder().typeMappings(['Pet.born': 'java.time.Instant']).build())

        then:
        result.diagnostics().empty
        result.ir().models()[0].fields()*.type() == [new TypeRef.Mapped('java.time.Instant', true), new TypeRef.Mapped('java.lang.Long', true)]
    }

    def "a field-level mapping of a field whose type is not a scalar is rejected"() {
        when:
        def result = build('''type Query { a: Pet }
type Pet { owner: Owner  tags: [Tag!]!  kind: Kind }
type Owner { name: String }
enum Tag { A }
enum Kind { B }''', OPTIONS.toBuilder().typeMappings(['Pet.owner': 'org.ext.Owner', 'Pet.tags': 'org.ext.Tag', 'Pet.kind': 'org.ext.Kind']).build())

        then:
        result.hasErrors()
        result.diagnostics()*.code() == [Diagnostic.FIELD_MAPPING_NOT_SCALAR] * 3
        result.diagnostics()[0].message().contains("'Pet.kind'")
        result.diagnostics()[0].message().contains('typeMappings.Kind')
        result.diagnostics()[0].origin().path() == 'schema.graphqls'
    }

    def "a mapped member makes its union and interface plain interfaces and is reported"() {
        when:
        def result = build('''type Query { s: Result  n: Node }
union Result = Pet | Dog
interface Node { id: ID! }
type Pet implements Node { id: ID! }
type Dog implements Node { id: ID! }''', OPTIONS.toBuilder().typeMappings([Dog: 'org.ext.Dog']).build())

        then:
        !result.hasErrors()
        result.ir().abstractTypes().collectEntries { [(it.names().sdlName()): it.sealed()] } == [Result: false, Node: false]
        result.ir().abstractTypes()*.members()*.collect { it.sdlName() }.every { it == ['Pet'] }
        result.diagnostics()*.code() == [Diagnostic.MAPPED_MEMBER, Diagnostic.MAPPED_MEMBER]
        result.diagnostics()*.optionName() == ['typeMappings.Dog', 'typeMappings.Dog']
    }

    def "without a mapped member the union and the interface stay sealed and nothing is reported"() {
        when:
        def result = build('''type Query { s: Result  n: Node }
union Result = Pet | Dog
interface Node { id: ID! }
type Pet implements Node { id: ID! }
type Dog implements Node { id: ID! }''')

        then:
        result.diagnostics().empty
        result.ir().abstractTypes()*.sealed() == [true, true]
    }

    def "the mappings of a dependency jar and a dependency directory are applied below typeMappings"() {
        given:
        def jar = dir.resolve('lib.jar')
        zip(jar, 'Pet=org.lib.Pet\nColor=org.lib.Color\nLong=org.lib.Wide\n')
        def classes = dir.resolve('classes')
        Files.createDirectories(classes.resolve('META-INF/micronaut-graphql-tools'))
        Files.writeString(classes.resolve(TypeMappingsFile.PATH), 'Owner=org.dir.Owner\n')

        when:
        def result = IrBuilder.fromSources([new SchemaSource('schema.graphqls', '''type Query { p: Pet  c: Color  o: Owner  l: Long  m: Mine }
type Pet { name: String }
enum Color { RED }
type Mine { n: Int }
type Owner { name: String }
scalar Long''')], OPTIONS.toBuilder().typeMappings([Color: 'org.mine.Color']).build(), [jar, classes])

        then:
        result.diagnostics().empty
        result.ir().models()*.names()*.sdlName() == ['Mine']
        result.ir().enums().empty
        result.ir().contracts()*.methods().flatten()*.returns()*.toString() == [
                new TypeRef.Mapped('org.lib.Pet', true), new TypeRef.Mapped('org.mine.Color', true),
                new TypeRef.Mapped('org.dir.Owner', true), new TypeRef.Mapped('org.lib.Wide', true),
                new TypeRef.Reference(result.ir().models()[0].names(), true)]*.toString()
    }

    def "the dependencies are ignored when the option is off, and a conflict or an unreadable jar is a warning"() {
        given:
        def one = dir.resolve('a.jar')
        def two = dir.resolve('b.jar')
        def broken = dir.resolve('c.jar')
        zip(one, 'Pet=org.a.Pet\n')
        zip(two, 'Pet=org.b.Pet\nBad=not a class\n')
        Files.writeString(broken, 'not a zip')
        def schema = [new SchemaSource('schema.graphqls', 'type Query { p: Pet }')]

        when:
        def off = IrBuilder.fromSources(schema, OPTIONS.toBuilder().typeMappingsFromDependencies(false).build(), [one])
        def on = IrBuilder.fromSources(schema, OPTIONS, [two, broken, one])

        then:
        off.diagnostics()*.code() == [Diagnostic.UNKNOWN_TYPE]
        !on.hasErrors()
        on.diagnostics()*.code() == [Diagnostic.DEPENDENCY_TYPE_MAPPINGS] * 3
        on.diagnostics()*.message().any { it.contains('map \'Pet\' to org.a.Pet and org.b.Pet') }
        on.ir().contracts()[0].methods()[0].returns() == new TypeRef.Mapped('org.a.Pet', true)
    }

    def "the written properties file lists the generated and mapped types, sorted, and is the same for any input order"() {
        given:
        def first = [new SchemaSource('a.graphqls', 'type Query { p: Pet  d: Date  o: Ext  l: Long }\nscalar Date\nscalar Long'),
                     new SchemaSource('b.graphqls', 'type Pet { name: String }\nenum Color { RED }\ninput In { a: Int }\ntype Ext { a: Int }\nunion U = Pet')]
        def options = OPTIONS.toBuilder().typeMappings([Date: 'java.time.LocalDate', Ext: 'org.ext.Ext']).build()
        def write = { sources ->
            def file = IrBuilder.fromSources(sources, options)
            assert !file.hasErrors()
            TypeMappingsFile.of(file.ir())
        }

        expect:
        write(first) == write(first.reverse())
        write(first).path() == 'META-INF/micronaut-graphql-tools/type-mappings.properties'
        new GoldenFiles().assertMatches('type-mappings', [write(first)]) == null
    }

    def "the generator returns the file, and a module with no type returns none"() {
        given:
        def schema = Files.writeString(dir.resolve('schema.graphqls'), 'type Query { p: Pet }\ntype Pet { name: String }')

        when:
        def result = GraphQLCodeGenerator.builder().withSchemaFiles(schema).withOutputDirectory(dir.resolve('out'))
                .withOptions(OPTIONS).build().generate()

        then:
        result.files()*.path() == [TypeMappingsFile.PATH]
        result.files()[0].content().contains('Pet=com.example.model.Pet\n')
        TypeMappingsFile.of(build('type Query { a: String }').ir()) == null
    }

    private void zip(Path jar, String content) {
        new ZipOutputStream(Files.newOutputStream(jar)).withCloseable { out ->
            out.putNextEntry(new ZipEntry(TypeMappingsFile.PATH))
            out.write(content.getBytes('ISO-8859-1'))
            out.closeEntry()
        }
    }

    private static IrResult build(String schema, Options options = OPTIONS) {
        IrBuilder.fromSources([new SchemaSource('schema.graphqls', schema)], options)
    }
}
