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

class JavaEmitterSpec extends Specification {

    @TempDir Path dir

    static final String ENUMS = '''"""
A colour of a thing.
Ends a comment with */ and names a tag @see here.
"""
enum Color {
  "The red one"
  RED
  GREEN @deprecated(reason: "Use RED")
  BLUE @deprecated
}
'''

    static final String EXTENSION = '''extend enum Color {
  "Added later"
  BLACK
}

enum Size { SMALL, MEDIUM }
'''

    def "the enums of a schema compile in memory and match the golden snapshot"() {
        given:
        def files = generate(write('a.graphqls', ENUMS), write('b.graphqls', EXTENSION))

        expect:
        new GoldenFiles().assertMatches('enum-java', files)
        new JavaInMemoryCompiler().assertCompiles(files)
    }

    def "the output is identical for every order of the schema files"() {
        given:
        def files = [write('a.graphqls', ENUMS), write('b.graphqls', EXTENSION)]

        expect:
        Determinism.assertDeterministic(files) { order -> generate(*order) }
    }

    def "a deprecated value has @Deprecated and a @deprecated tag, and the constants keep the schema names"() {
        when:
        def color = generate(write('a.graphqls', ENUMS), write('b.graphqls', EXTENSION)).find { it.path() == 'com/example/model/Color.java' }.content()

        then:
        color.contains('    @Deprecated\n    GREEN,')
        color.contains(' * @deprecated Use RED\n')
        color.contains('    BLACK')
        color.contains('@Generated("io.micronaut.graphql.tools.codegen")')
    }

    def "a comment terminator and a tag in a description cannot break the Javadoc"() {
        when:
        def color = generate(write('a.graphqls', ENUMS)).find { it.path() == 'com/example/model/Color.java' }.content()

        then:
        color.count('*/') == 4
        color.contains('Ends a comment with *&#47; and names a tag &#64;see here.')
    }

    def "each package that holds a generated type has a package-info.java"() {
        when:
        def files = generate(write('a.graphqls', ENUMS), write('b.graphqls', EXTENSION))

        then:
        files*.path() == ['com/example/model/Color.java', 'com/example/model/Size.java', 'com/example/model/package-info.java']
        files.last().content() == 'package com.example.model;\n'
    }

    def "a schema without an enum has no file"() {
        expect:
        generate(write('a.graphqls', 'type Query { hello: String }')).empty
    }

    private List<io.micronaut.graphql.tools.codegen.GeneratedFile> generate(Path... schemaFiles) {
        GraphQLCodeGenerator.builder().withSchemaFiles(schemaFiles).withOutputDirectory(dir.resolve('out'))
                .withOptions(Options.builder().packageName('com.example').build()).build().generate().files()
    }

    private Path write(String name, String text) {
        Files.writeString(dir.resolve(name), text)
    }
}
