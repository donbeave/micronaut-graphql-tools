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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GraphQLCodeGeneratorSpec extends Specification {

    @TempDir Path dir

    def "a valid schema and valid options give a result without diagnostics"() {
        given:
        def schema = write('schema.graphqls', 'type Query { hello: String }')

        when:
        def result = generator(schema, Options.builder().packageName('com.example').build()).generate()

        then:
        result.diagnostics().empty
        !result.hasErrors()
        result.files().empty
    }

    def "the builder takes a list of files too, and keeps the output directory"() {
        given:
        def schema = write('schema.graphqls', 'type Query { hello: String }')
        def generator = GraphQLCodeGenerator.builder().withSchemaFiles([schema]).withOutputDirectory(dir.resolve('out'))
                .withOptions(Options.builder().packageName('p').build()).build()

        expect:
        generator.getOutputDirectory() == dir.resolve('out')
        generator.generate().diagnostics().empty
    }

    def "an invalid option is a diagnostic that names the option"() {
        given:
        def schema = write('schema.graphqls', 'type Query { hello: String }')

        when:
        def result = generator(schema, Options.builder().build()).generate()

        then:
        result.hasErrors()
        result.diagnostics()*.optionName() == ['packageName']
    }

    def "a missing schema file is a diagnostic with its path"() {
        when:
        def result = generator(dir.resolve('missing.graphqls'), Options.builder().packageName('p').build()).generate()

        then:
        result.diagnostics().size() == 1
        result.diagnostics()[0].code() == Diagnostic.SCHEMA_FILE_UNREADABLE
        result.diagnostics()[0].origin().path().endsWith('missing.graphqls')
    }

    def "a syntax error is a diagnostic with its line and column"() {
        given:
        def schema = write('bad.graphqls', 'type Query {\n  hello: String\n\ntype Other { a: Int }')

        when:
        def result = generator(schema, Options.builder().packageName('p').build()).generate()

        then:
        result.diagnostics().size() == 1
        result.diagnostics()[0].code() == Diagnostic.SCHEMA_SYNTAX_ERROR
        result.diagnostics()[0].origin().line() > 0
        result.diagnostics()[0].origin().path().endsWith('bad.graphqls')
        result.files().empty
    }

    def "the result keeps the diagnostics of the options and of the files together"() {
        given:
        def schema = write('bad.graphqls', 'type {')

        when:
        def result = generator(schema, Options.builder().build()).generate()

        then:
        result.diagnostics()*.code() == [Diagnostic.MISSING_OPTION, Diagnostic.SCHEMA_SYNTAX_ERROR]
    }

    def "a generator that is wired wrongly fails to build"() {
        when:
        GraphQLCodeGenerator.builder().withSchemaFiles(dir.resolve('a')).withSchemaDirectory(dir).withOutputDirectory(dir)
                .withOptions(Options.builder().build()).build()

        then:
        thrown(IllegalStateException)

        when:
        GraphQLCodeGenerator.builder().withSchemaFiles(dir.resolve('a')).withOptions(Options.builder().build()).build()

        then:
        thrown(IllegalStateException)

        when:
        GraphQLCodeGenerator.builder().withSchemaFiles(dir.resolve('a')).withOutputDirectory(dir).build()

        then:
        thrown(IllegalStateException)
    }

    private GraphQLCodeGenerator generator(Path schema, Options options) {
        GraphQLCodeGenerator.builder().withSchemaFiles(schema).withOutputDirectory(dir.resolve('out')).withOptions(options).build()
    }

    private Path write(String name, String text) {
        Files.writeString(dir.resolve(name), text)
    }
}
