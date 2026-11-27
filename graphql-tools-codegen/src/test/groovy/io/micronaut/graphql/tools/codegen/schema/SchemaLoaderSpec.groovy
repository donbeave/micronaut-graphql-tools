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
import io.micronaut.graphql.tools.codegen.Origin
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

class SchemaLoaderSpec extends Specification {

    @TempDir Path dir

    static final Options OPTIONS = Options.builder().packageName('com.example').build()

    def "the default includes read .graphqls and .graphql files, in sorted order, and skip operation files"() {
        given:
        write('z.graphqls', 'type Query { z: String }')
        write('b/inner.graphqls', 'extend type Query { b: String }')
        write('a.graphql', 'extend type Query { a: String }')
        write('ops.graphql', 'query Q { z }')
        write('notes.txt', 'type Query { ignored: String }')
        write('comments.graphqls', '# only a comment\n')

        when:
        def result = IrBuilder.fromDirectory(dir, OPTIONS)

        then:
        result.diagnostics().empty
        result.ir().contracts()*.names()*.sdlName() == ['Query.z', 'Query.a', 'Query.b']
        result.skippedFiles() == ['comments.graphqls', 'ops.graphql']
    }

    def "includes and excludes select the files by glob on the relative path"() {
        given:
        write('keep.graphqls', 'type Query { keep: String }')
        write('client/ops.graphqls', 'extend type Query { client: String }')
        write('gen/skip.graphqls', 'extend type Query { skip: String }')
        write('deep/er/more.graphqls', 'extend type Query { more: String }')

        when:
        def result = IrBuilder.fromDirectory(dir, OPTIONS.toBuilder().includes(includes).excludes(excludes).build())

        then:
        result.ir().contracts()*.names()*.sdlName() == fields

        where:
        includes                       | excludes                    || fields
        ['**/*.graphqls']              | []                          || ['Query.keep', 'Query.client', 'Query.more', 'Query.skip']
        ['**/*.graphqls']              | ['gen/**', 'client/*.graphqls'] || ['Query.keep', 'Query.more']
        ['*.graphqls']                 | []                          || ['Query.keep']
        ['{keep,deep/**/more}.graphqls'] | []                        || ['Query.keep', 'Query.more']
        ['deep/**', 'keep.*']          | []                          || ['Query.keep', 'Query.more']
    }

    def "a glob keeps a dot, a plus and an unbalanced brace literal"() {
        expect:
        Globs.compile('a.b+c{').matcher('a.b+c{').matches()
        !Globs.compile('a.b').matcher('axb').matches()
        Globs.compile('**/x').matcher('x').matches()
        Globs.compile('**/x').matcher('p/q/x').matches()
        !Globs.compile('*.g').matcher('p/a.g').matches()
        Globs.compile('?.g').matcher('a.g').matches()
    }

    def "a directory without a matching file is a diagnostic that names the option"() {
        given:
        write('notes.txt', 'text')

        when:
        def result = IrBuilder.fromDirectory(dir, OPTIONS)

        then:
        result.diagnostics()*.code() == [Diagnostic.NO_SCHEMA_FILES]
        result.diagnostics()[0].optionName() == 'includes'
        result.ir().models().empty
    }

    def "a missing directory is a diagnostic"() {
        when:
        def result = IrBuilder.fromDirectory(dir.resolve('missing'), OPTIONS)

        then:
        result.diagnostics()*.code() == [Diagnostic.SCHEMA_FILE_UNREADABLE]
        result.diagnostics()[0].message().contains('does not exist')
    }

    def "the generator reads the default directory when the builder names no schema"() {
        given:
        def generator = GraphQLCodeGenerator.builder().withOutputDirectory(dir).withOptions(OPTIONS).build()

        expect:
        GraphQLCodeGenerator.DEFAULT_SCHEMA_DIRECTORY == 'src/main/resources/graphql'
        generator.toIr().diagnostics()*.code() == [Diagnostic.SCHEMA_FILE_UNREADABLE]
    }

    def "the generator reads a schema directory and the origins are relative to it"() {
        given:
        write('sub/schema.graphqls', 'type Query { hello: Unknown }')

        when:
        def result = GraphQLCodeGenerator.builder().withSchemaDirectory(dir).withOutputDirectory(dir.resolve('out'))
                .withOptions(OPTIONS).build().generate()

        then:
        result.diagnostics()*.code() == [Diagnostic.UNKNOWN_TYPE]
        result.diagnostics()[0].origin() == new Origin('sub/schema.graphqls', 1, 14)
    }

    def "files and a directory are alternatives"() {
        when:
        GraphQLCodeGenerator.builder().withSchemaDirectory(dir).withSchemaFiles(dir.resolve('a')).withOutputDirectory(dir)
                .withOptions(OPTIONS).build()

        then:
        thrown(IllegalStateException)
    }

    def "explicit files are de-duplicated and relative to their closest common directory"() {
        given:
        def a = write('x/a.graphqls', 'type Query { a: String }')
        def b = write('x/y/b.graphqls', 'extend type Query { b: Missing }')

        when:
        def result = IrBuilder.fromFiles([b, a, a.resolve('..').resolve('a.graphqls'), b], OPTIONS)

        then:
        result.diagnostics()*.origin() == [new Origin('y/b.graphqls', 1, 21)]
    }

    def "unreadable explicit files are reported in path order for any input order"() {
        given:
        def b = dir.resolve('b.graphqls')
        def a = dir.resolve('a.graphqls')

        expect:
        IrBuilder.fromFiles([b, a], OPTIONS).diagnostics()*.origin()*.path() == ['a.graphqls', 'b.graphqls']
        IrBuilder.fromFiles([a, b], OPTIONS).diagnostics()*.origin()*.path() == ['a.graphqls', 'b.graphqls']
    }

    def "the same definition in two files is a diagnostic that names both places"() {
        given:
        write('a.graphqls', 'type Query { a: String }\ntype Dup { x: Int }')
        write('b.graphqls', 'type Dup { y: Int }')

        when:
        def result = IrBuilder.fromDirectory(dir, OPTIONS)

        then:
        result.diagnostics()*.code() == [Diagnostic.DUPLICATE_DEFINITION]
        result.diagnostics()[0].message() == "The type 'Dup' is defined twice: at a.graphqls:2:1 and at b.graphqls:1:1."
        result.diagnostics()[0].origin() == new Origin('b.graphqls', 1, 1)
    }

    def "a field that an extension repeats is a diagnostic"() {
        given:
        write('a.graphqls', 'type Query { a: String }')
        write('b.graphqls', 'extend type Query { a: Int }')

        when:
        def result = IrBuilder.fromDirectory(dir, OPTIONS)

        then:
        result.diagnostics()*.code() == [Diagnostic.DUPLICATE_DEFINITION]
        result.diagnostics()[0].message() == "The field 'Query.a' is defined twice: at a.graphqls:1:14 and at b.graphqls:1:21."
    }

    @Unroll
    def "an extension that #kind is a diagnostic"() {
        given:
        write('a.graphqls', 'type Query { a: String }\nenum E { X }')
        write('b.graphqls', extension)

        when:
        def result = IrBuilder.fromDirectory(dir, OPTIONS)

        then:
        result.diagnostics()*.code() == [code]
        result.diagnostics()[0].message() == message
        result.diagnostics()[0].origin() == new Origin('b.graphqls', 1, 1)

        where:
        kind                | extension               | code                                  | message
        'has no base'       | 'extend type Nothing { a: Int }' | Diagnostic.EXTENSION_WITHOUT_BASE | "The extension of the type 'Nothing' at b.graphqls:1:1 has no base definition of that name in the schema files."
        'has another kind'  | 'extend type E { a: Int }' | Diagnostic.EXTENSION_KIND_MISMATCH | "The extension at b.graphqls:1:1 is an extension of an object type, but 'E' is an enum type (defined at a.graphqls:2:1)."
    }

    def "a syntax error names the file, the line and the column"() {
        given:
        write('bad.graphqls', 'type Query {\n  a: String\n\ntype Other { a: Int }')

        when:
        def result = IrBuilder.fromDirectory(dir, OPTIONS)

        then:
        result.diagnostics()*.code() == [Diagnostic.SCHEMA_SYNTAX_ERROR]
        result.diagnostics()[0].origin().path() == 'bad.graphqls'
        result.diagnostics()[0].origin().line() == 4
        result.ir().models().empty
    }

    def "a byte order mark does not break the first definition"() {
        given:
        write('a.graphqls', '﻿type Query { a: String }')

        expect:
        IrBuilder.fromDirectory(dir, OPTIONS).diagnostics().empty
    }

    def "an argument that a field repeats is a diagnostic that names both places"() {
        when:
        def result = IrBuilder.fromSources([new SchemaSource('a.graphqls', 'type Query { f(x: Int, x: String): String }')], OPTIONS)

        then:
        result.diagnostics()*.code() == [Diagnostic.DUPLICATE_DEFINITION]
        result.diagnostics()[0].message() == "The argument 'Query.f(x)' is defined twice: at a.graphqls:1:16 and at a.graphqls:1:24."
    }

    def "two sources with one path and different text are a diagnostic, and the same text is read once"() {
        given:
        def one = new SchemaSource('a.graphqls', 'type Query { a: Int }')
        def other = new SchemaSource('a.graphqls', 'type Query { b: Int }')

        expect:
        IrBuilder.fromSources([one, one], OPTIONS).diagnostics().empty
        IrBuilder.fromSources([one, other], OPTIONS).diagnostics()*.code() == [Diagnostic.DUPLICATE_DEFINITION]
        IrBuilder.fromSources([other, one], OPTIONS).diagnostics()*.code() == [Diagnostic.DUPLICATE_DEFINITION]
        IrBuilder.fromSources([one, other], OPTIONS).diagnostics()[0].message() ==
                'The schema source a.graphqls is given twice with different content.'
    }

    def "a byte order mark in a source is dropped like in a file"() {
        expect:
        IrBuilder.fromSources([new SchemaSource('a.graphqls', '\uFEFFtype Query { a: String }')], OPTIONS).diagnostics().empty
    }

    def "an extend schema block adds its roots"() {
        when:
        def result = IrBuilder.fromSources([
                new SchemaSource('a.graphqls', 'schema { query: Q }\ntype Q { a: Int }\ntype M { b: Int }'),
                new SchemaSource('b.graphqls', 'extend schema { mutation: M }')], OPTIONS)

        then:
        result.diagnostics().empty
        result.skippedFiles().empty
        result.ir().contracts()*.names()*.sdlName() == ['Q.a', 'M.b']
        result.ir().models().empty
    }

    private Path write(String path, String text) {
        def file = dir.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }
}
