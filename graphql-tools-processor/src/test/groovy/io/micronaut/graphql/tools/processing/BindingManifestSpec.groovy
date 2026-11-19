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
package io.micronaut.graphql.tools.processing

import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class BindingManifestSpec extends Specification {

    void "a compilation that passes the checks writes the manifest to the class output, UTF-8 with LF, body sorted"() {
        when:
        def result = ResolverChecksSpec.compilation([:]).compile()
        def file = new File(result.classOutput, 'META-INF/micronaut-graphql-tools/binding-manifest.txt')
        def bytes = Files.readAllBytes(file.toPath())
        def text = new String(bytes, StandardCharsets.UTF_8)
        def lines = text.split('\n', -1).toList()
        def body = lines.findAll { !it.startsWith('#') && !it.isEmpty() }

        then:
        result.success
        file.isFile()
        !text.contains('\r')
        text.endsWith('\n')
        lines[0] == '# graphql-binding-manifest 2'
        lines[1].startsWith('# binder: micronaut-graphql-tools ')
        lines[2] ==~ /# graphql-java: \d+\.\d+(\.\d+)?|# graphql-java: unknown/
        lines[3] ==~ /# schema-sha256: [0-9a-f]{64}/
        body == body.toSorted { a, b -> a <=> b }
        body.contains('Query.book(id: ID!): Book -> root ; example.BookQuery#book(java.lang.String) ; example.Book ; example.Book')
        bytes.length == text.length()
        text.every { (it as char) <= (0x7e as char) }
    }

    void "a compilation that fails the checks writes no manifest"() {
        when:
        def result = ResolverChecksSpec.compilation(['example.BookResolver': null]).compile()

        then:
        !result.success
        result.manifest == null
    }

    void "a compilation without resolvers fails and writes no manifest: there is nothing bound to describe"() {
        when:
        def result = new Compilation()
                .schema('schema.graphqls', ResolverChecksSpec.SCHEMA)
                .source('example.Book', ResolverChecksSpec.BOOK)
                .compile()

        then:
        !result.success
        result.manifest == null
        result.omitted == null
    }

    void "manifest=false writes no manifest; an invalid value fails"() {
        when:
        def disabled = ResolverChecksSpec.compilation([:]).option(ProcessorOptions.MANIFEST, 'false').compile()
        def invalid = ResolverChecksSpec.compilation([:]).option(ProcessorOptions.MANIFEST, 'maybe').compile()

        then:
        disabled.success
        disabled.manifest == null
        disabled.omitted == null
        !invalid.success
        invalid.graphqlErrors == ['[graphql] micronaut.graphql.tools.manifest must be true or false (or yes/no, on/off), not maybe.']
        invalid.manifest == null
    }

    void "next to the manifest, the processor records what it left out, so that a comparison does not need a list of its own"() {
        when:
        def result = ResolverChecksSpec.compilation([:]).compile()

        then:
        result.success
        result.omitted == '# graphql-binding-manifest-omitted 1\nkind resolves\nkind scalar\nkind unusedDictionary\nkind unwiredDirective\n'
    }

    void "the schema hash of the header ignores the order of the schema files and of the definitions"() {
        given:
        def first = 'schema {\n  query: Query\n}\n\ntype Query {\n  book(id: ID!): Book\n  books(limit: Int): [Book!]!\n}\n'
        def second = 'type Book {\n  title: String!\n  author: String\n}\n'

        when:
        def together = ResolverChecksSpec.compilation([:]).compile()
        def split = ResolverChecksSpec.compilation([:], second + '\n' + first).compile()

        then:
        together.success
        split.success
        together.manifest.readLines()[3] == split.manifest.readLines()[3]
    }

}
