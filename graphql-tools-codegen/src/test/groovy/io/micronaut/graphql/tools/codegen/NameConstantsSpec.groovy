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
package io.micronaut.graphql.tools.codegen

import io.micronaut.graphql.tools.codegen.golden.Determinism
import io.micronaut.graphql.tools.codegen.golden.GoldenFiles
import io.micronaut.graphql.tools.codegen.golden.JavaInMemoryCompiler
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class NameConstantsSpec extends Specification {

    @TempDir Path dir

    static final String SCHEMA = '''type Query {
  pet(id: ID!, fooBar: String): Pet
}

interface Node {
  id: ID!
}

type Pet implements Node {
  id: ID!
  v2Api: String
  HTTPStatus: Int
}

input PetInput {
  fooBar: String
}

enum Color { RED }

union Thing = Pet

scalar Date
'''

    static final Map<String, String> MAPPINGS = [Date: 'java.time.LocalDate']

    // The interface and the union of SCHEMA are written by the sealed-interface emission (#127); the record Pet needs them to compile.
    static final List<GeneratedFile> INTERFACES = [
            new GeneratedFile('com/example/model/Node.java', 'package com.example.model;\n\npublic interface Node {\n    String id();\n}\n'),
            new GeneratedFile('com/example/model/Thing.java', 'package com.example.model;\n\npublic interface Thing {\n}\n')
    ]

    def "the name classes of a schema compile in memory and match the golden snapshot"() {
        given:
        def files = generate(Options.builder().packageName('com.example').typeMappings(MAPPINGS), write('a.graphqls', SCHEMA))

        expect:
        new GoldenFiles().assertMatches('name-constants-java', files)
        new JavaInMemoryCompiler().assertCompiles(files + INTERFACES)
    }

    def "the constants are the same for every order of the schema files"() {
        given:
        def files = [write('a.graphqls', SCHEMA), write('b.graphqls', 'enum Size { SMALL }')]

        expect:
        Determinism.assertDeterministic(files) { order -> generate(Options.builder().packageName('com.example').build(), *order) }
    }

    def "the type constant and the field constants are in upper snake case, and the name classes follow the types"() {
        when:
        def pet = generate(Options.builder().packageName('com.example').typeMappings(MAPPINGS), write('a.graphqls', SCHEMA))
                .find { it.path() == 'com/example/names/PetNames.java' }.content()

        then:
        pet.contains('    public static final String TYPE = "Pet";\n')
        pet.contains('    public static final String V2_API = "v2Api";\n')
        pet.contains('    public static final String HTTP_STATUS = "HTTPStatus";\n')
        pet.contains('    public static final String ID = "id";\n')
    }

    def "the names of the enums, the unions and the scalars share the class GraphQLTypeNames"() {
        when:
        def files = generate(Options.builder().packageName('com.example').typeMappings(MAPPINGS), write('a.graphqls', SCHEMA))
        def names = files.find { it.path() == 'com/example/names/GraphQLTypeNames.java' }.content()

        then:
        names.contains('public static final String COLOR = "Color";')
        names.contains('public static final String THING = "Thing";')
        names.contains('public static final String DATE = "Date";')
        !names.contains('PET')
    }

    def "TYPES puts every type name in GraphQLTypeNames and emits no class per type"() {
        when:
        def files = generate(Options.builder().packageName('com.example').nameConstants(NameConstants.TYPES).typeMappings(MAPPINGS),
                write('a.graphqls', SCHEMA))

        then:
        files*.path().findAll { it.endsWith('Names.java') } == ['com/example/names/GraphQLTypeNames.java']
        files.find { it.path() == 'com/example/names/GraphQLTypeNames.java' }.content().contains('String PET = "Pet";')
    }

    def "NONE emits no name class"() {
        expect:
        generate(Options.builder().packageName('com.example').nameConstants(NameConstants.NONE).typeMappings(MAPPINGS),
                write('a.graphqls', SCHEMA))*.path().findAll { it.contains('/names/') }.empty
    }

    def "a field whose constant collides with another field of its type is a diagnostic that names both"() {
        given:
        def schema = write('a.graphqls', 'type Query { hello: String }\ntype Pet { fooBar: String, foo_bar: String }')

        when:
        def result = GraphQLCodeGenerator.builder().withSchemaFiles(schema).withOutputDirectory(dir.resolve('out'))
                .withOptions(Options.builder().packageName('com.example').build()).build().generate()

        then:
        result.hasErrors()
        result.files().empty
        def diagnostic = result.diagnostics().find { it.code() == Diagnostic.NAME_COLLISION }
        diagnostic.message().contains('FOO_BAR')
        diagnostic.message().contains("the field 'Pet.fooBar'")
        diagnostic.message().contains("the field 'Pet.foo_bar'")
    }

    def "a field named type collides with the type constant of its class"() {
        given:
        def schema = write('a.graphqls', 'type Query { hello: String }\ntype Pet { type: String }')

        when:
        def result = GraphQLCodeGenerator.builder().withSchemaFiles(schema).withOutputDirectory(dir.resolve('out'))
                .withOptions(Options.builder().packageName('com.example').build()).build().generate()

        then:
        result.diagnostics().find { it.code() == Diagnostic.NAME_COLLISION }.message().contains("the type 'Pet'")
    }

    def "two shared names with one identifier collide in GraphQLTypeNames"() {
        given:
        def schema = write('a.graphqls', 'type Query { hello: String }\nenum HttpStatus { OK }\nscalar HTTP_STATUS')

        when:
        def result = GraphQLCodeGenerator.builder().withSchemaFiles(schema).withOutputDirectory(dir.resolve('out'))
                .withOptions(Options.builder().packageName('com.example').typeMappings([HTTP_STATUS: 'java.lang.String']).build())
                .build().generate()

        then:
        def diagnostic = result.diagnostics().find { it.code() == Diagnostic.NAME_COLLISION }
        diagnostic.message().contains('HTTP_STATUS in the class com.example.names.GraphQLTypeNames')
    }

    private List<GeneratedFile> generate(Options.Builder options, Path... schemaFiles) {
        generate(options.build(), schemaFiles)
    }

    private List<GeneratedFile> generate(Options options, Path... schemaFiles) {
        GraphQLCodeGenerator.builder().withSchemaFiles(schemaFiles).withOutputDirectory(dir.resolve('out'))
                .withOptions(options).build().generate().files()
    }

    private Path write(String name, String text) {
        Files.writeString(dir.resolve(name), text)
    }
}
