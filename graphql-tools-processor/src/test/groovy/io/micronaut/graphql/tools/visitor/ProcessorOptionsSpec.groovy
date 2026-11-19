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
package io.micronaut.graphql.tools.visitor

import spock.lang.Specification

/** The options and the failure modes of the compilation : nothing is skipped silently. */
class ProcessorOptionsSpec extends Specification {

    static final String SCHEMA = 'type Query { ping: String }\n'

    static final String RESOLVER = '''
package example;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
@GraphQLRootResolver
public class QueryResolver {
    public String stray() { return ""; }
}'''

    private static VisitorCompilation compilation() {
        new VisitorCompilation().schema('schema.graphqls', SCHEMA).source('example.QueryResolver', RESOLVER)
    }

    def "the checks run by default and by checks=true; checks=false says so once and checks nothing"() {
        expect:
        compilation().compile().findings == ['[unbound-resolver-method] example.QueryResolver#stray()']
        compilation().option(ProcessorOptions.CHECKS, 'TRUE').compile().findings.size() == 1

        when:
        def off = compilation().option(ProcessorOptions.CHECKS, 'false').compile()

        then:
        off.success
        off.notes == ['[graphql-tools] GraphQL compile-time checks are off (micronaut.graphql.tools.processor.checks=false); startup still runs every check.']
    }

    def "an unknown key under the processor segment is an error that names the valid keys"() {
        when:
        def result = compilation().option('micronaut.graphql.tools.processor.schema.dir', '/x').compile()

        then:
        !result.success
        result.findings == ['[processor-option-unknown] -']
        result.errors[0].contains('micronaut.graphql.tools.processor.checks')
        result.errors[0].contains('micronaut.graphql.tools.processor.schema.dirs')
    }

    def "a runtime key that is present as a system property is not an option of the visitor"() {
        when:
        def result = compilation().option('micronaut.graphql.tools.strict', 'false').option('micronaut.graphql.tools.schema.locations', 'x')
                .compile()

        then:
        result.findings == ['[unbound-resolver-method] example.QueryResolver#stray()']
    }

    def "the key of the removed JSR-269 processor is an error that names the new key"() {
        when:
        def result = compilation().option('micronaut.graphql.tools.sdlDir', '/x').compile()

        then:
        !result.success
        result.findings == ['[processor-option-removed] -']
        result.errors[0].contains(ProcessorOptions.SCHEMA_DIRS)
    }

    def "a value that is not a boolean is an error"() {
        when:
        def result = compilation().option(ProcessorOptions.CHECKS, 'maybe').compile()

        then:
        result.findings == ['[processor-option-invalid] -']
    }

    def "the accessor prefixes option is off by default, reads a boolean and rejects anything else"() {
        expect:
        !ProcessorOptions.read([:]).accessorPrefixes()
        ProcessorOptions.read([(ProcessorOptions.ACCESSOR_PREFIXES): value]).accessorPrefixes() == expected
        ProcessorOptions.read([(ProcessorOptions.ACCESSOR_PREFIXES): value]).errors().empty

        where:
        value   | expected
        'true'  | true
        'YES'   | true
        'on'    | true
        'false' | false
        'off'   | false
    }

    def "an accessor prefixes value that is not a boolean is an error"() {
        when:
        def options = ProcessorOptions.read([(ProcessorOptions.ACCESSOR_PREFIXES): 'maybe'])

        then:
        options.errors()*.code() == ['processor-option-invalid']
        options.errors()[0].sentence().contains(ProcessorOptions.ACCESSOR_PREFIXES)
    }

    def "a directory that this system cannot use as a path is an option error, not a crash of the compiler"() {
        when:
        def options = ProcessorOptions.read([(ProcessorOptions.SCHEMA_DIRS): "ok${File.pathSeparator}bad\u0000path".toString()])

        then:
        options.errors()*.code() == ['processor-option-invalid']
        options.errors()[0].sentence().contains(ProcessorOptions.SCHEMA_DIRS)
        options.schemaDirs()*.toString() == ['ok']
    }

    def "without a schema directory the build fails on the first class it would check, unless checks=false"() {
        when:
        def result = new VisitorCompilation().withoutSchemaOption().source('example.QueryResolver', RESOLVER).compile()

        then:
        result.findings == ['[processor-schema-not-configured] -']
        result.errors[0].contains(ProcessorOptions.SCHEMA_DIRS)
        result.errors[0].contains(ProcessorOptions.CHECKS + '=false')
        new VisitorCompilation().withoutSchemaOption().option(ProcessorOptions.CHECKS, 'false').source('example.QueryResolver', RESOLVER)
                .compile().success
    }

    def "a directory that does not exist, and a directory without a schema file, fail the build"() {
        when:
        def missing = new VisitorCompilation().withoutSchemaOption().option(ProcessorOptions.SCHEMA_DIRS, '/no/such/dir')
                .source('example.QueryResolver', RESOLVER).compile()
        def empty = new VisitorCompilation().schema('readme.txt', 'no schema').source('example.QueryResolver', RESOLVER).compile()

        then:
        missing.findings == ['[processor-schema-not-found] -']
        empty.findings == ['[processor-schema-not-found] -']
    }

    def "a schema that does not parse fails the build with the file, and the classes are not checked against it"() {
        when:
        def result = new VisitorCompilation().schema('schema.graphqls', 'type Query { ping: }').source('example.QueryResolver', RESOLVER)
                .compile()

        then:
        result.findings == ['[schema-invalid] -']
        result.errors[0].contains('schema.graphqls')
    }

    def "several directories are read, and an extension in one adds fields to a type of another"() {
        given:
        def extra = File.createTempDir('graphql-visitor-extra')
        new File(extra, 'extension.graphqls').text = 'extend type Query { stray: String }\n'

        when:
        def result = compilation().extraSchemaDirectory(extra).compile()

        then:
        result.success
    }

    def "module.dir is read as an absolute path, and a path that cannot be used is an option error"() {
        expect:
        ProcessorOptions.read([:]).moduleDir() == null
        ProcessorOptions.read([(ProcessorOptions.MODULE_DIR): '/work/books'])
                .moduleDir() == java.nio.file.Path.of('/work/books')
        ProcessorOptions.read([(ProcessorOptions.MODULE_DIR): "bad\u0000path".toString()]).errors()*.code() == ['processor-option-invalid']
    }

    def "options that an earlier module of the same JVM left behind are rejected, naming the module"() {
        when:
        def stale = compilation().option(ProcessorOptions.MODULE_DIR, '/work/authors').compile()

        then:
        stale.findings == ['[processor-schema-not-configured] -']
        stale.errors[0].contains('/work/authors')
        stale.errors[0].contains('this compilation writes to')
        stale.errors[0].contains(ProcessorOptions.CHECKS + '=false')
        stale.errors[0].contains('outside the project directory')

        expect:
        compilation().option(ProcessorOptions.MODULE_DIR, '/work/authors').option(ProcessorOptions.CHECKS, 'false').compile().success
    }

    def "module.dir that contains the output of the compilation is the module's own, and without it nothing changes"() {
        expect:
        compilation().ownModuleDirOption().compile().findings == ['[unbound-resolver-method] example.QueryResolver#stray()']
        compilation().compile().findings == ['[unbound-resolver-method] example.QueryResolver#stray()']
    }
}
