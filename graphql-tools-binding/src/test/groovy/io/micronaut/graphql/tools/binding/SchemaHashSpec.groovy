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
package io.micronaut.graphql.tools.binding

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.graphql.tools.binding.internal.manifest.SchemaHash
import spock.lang.Specification

class SchemaHashSpec extends Specification {

    static final List<String> FILES = [
            'type Query { book: Book }',
            'type Book { id: ID! }',
            'extend type Book { title: String }',
            'extend type Book { pages: Int }',
            'interface Node { id: ID! }',
            'extend interface Node { tag: String }',
            'extend interface Node { label: String }',
            'enum Genre { NOVEL }',
            'extend enum Genre { POETRY }',
            'extend enum Genre { DRAMA }',
            'input Filter { a: Int }',
            'extend input Filter { b: Int }',
            'extend input Filter { c: Int }',
            'union Hit = Book',
            'extend union Hit = Query',
            'scalar Date',
            'extend scalar Date @specifiedBy(url: "https://example.org")',
            'directive @tag on FIELD_DEFINITION',
            'schema { query: Query }',
            'extend schema @tag',
    ]

    private static String hash(List<String> files) {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        files.each { registry.merge(new SchemaParser().parse(it)) }
        SchemaHash.sha256(registry) { false }
    }

    void "the hash is 64 hex digits"() {
        expect:
        hash(FILES) ==~ /[0-9a-f]{64}/
    }

    void "the files in any order, with their extensions in any order, give one hash"() {
        given:
        String expected = hash(FILES)

        expect:
        (1..40).every { seed ->
            List<String> shuffled = new ArrayList<>(FILES)
            Collections.shuffle(shuffled, new Random(seed))
            hash(shuffled) == expected
        }
        hash(FILES.reverse()) == expected
    }

    void "the definitions of one file in any order give the hash of the same definitions in separate files"() {
        expect:
        hash([FILES.join('\n')]) == hash(FILES)
        hash([FILES.reverse().join('\n')]) == hash(FILES)
    }

    void "extensions of one type in one file in any order give one hash"() {
        expect:
        hash(['type A { a: Int }', 'extend type A { b: Int }', 'extend type A { c: Int }']) ==
                hash(['type A { a: Int }', 'extend type A { c: Int }', 'extend type A { b: Int }'])
    }

    void "comments and white space do not change the hash"() {
        expect:
        hash(['type A { a: Int }']) == hash(['# a comment\ntype   A   {\n\n  a : Int # trailing\n}'])
    }

    void "a change of the schema changes the hash"() {
        expect:
        hash(FILES) != hash(FILES.findAll { it != 'extend enum Genre { DRAMA }' })
        hash(FILES) != hash(FILES.collect { it == 'type Book { id: ID! }' ? 'type Book { id: ID }' : it })
    }

    void "the order of the fields inside one definition is part of the hash"() {
        expect:
        hash(['type A { a: Int b: Int }']) != hash(['type A { b: Int a: Int }'])
    }
}
