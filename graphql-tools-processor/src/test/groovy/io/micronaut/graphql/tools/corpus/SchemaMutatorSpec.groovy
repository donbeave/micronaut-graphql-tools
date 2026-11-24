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
package io.micronaut.graphql.tools.corpus

import graphql.parser.Parser
import spock.lang.Specification

/**
 * The tests of the mutator of the drift mutation tests (#89): which mutants it makes of a small schema, that a mutant is a valid
 * document that differs from its base, that the equivalent ones keep the set of definitions, and that a seed fixes the result.
 */
class SchemaMutatorSpec extends Specification {

    static final Map<String, String> FILES = [
            'a.graphqls': '''
                type Query { book(id: ID!, filter: Filter): Book  books(limit: Int, offset: Int): [Book!]! }
                type Book { title: String! tags: [String] }
                input Filter { text: String! }
                enum Level { LOW HIGH }
                extend type Query { shelf: String }
            ''',
            'b.graphqls': 'extend type Book { isbn: String }',
    ]

    void "a schema has a breaking mutant of every kind"() {
        expect:
        SchemaMutator.mutants(FILES, 1L).findAll { it.breaking }*.kind as Set == [
                'rename-field', 'toggle-nullability', 'change-list-wrapping', 'retype-scalar', 'toggle-nullability-argument',
                'change-list-wrapping-argument', 'retype-scalar-argument', 'toggle-nullability-input-field',
                'change-list-wrapping-input-field', 'retype-scalar-input-field', 'add-unmapped-input-property', 'drop-enum-value',
        ] as Set
    }

    void "a schema has an equivalent mutant of every kind"() {
        expect:
        SchemaMutator.mutants(FILES, 1L).findAll { !it.breaking }*.kind as Set == [
                'move-field-to-extension', 'reorder-arguments', 'shuffle-definitions', 'extensions-first', 'shuffle-files',
                'shuffle-across-files',
        ] as Set
    }

    void "every mutant is a valid document and none is the unchanged schema"() {
        expect:
        SchemaMutator.mutants(FILES, 1L).each { Mutant m ->
            m.files.values().each { new Parser().parseDocument(it) }
            assert m.files != FILES : m.id
        }
    }

    void "the mutants are a function of the schema and the seed"() {
        expect:
        SchemaMutator.mutants(FILES, 7L)*.files == SchemaMutator.mutants(FILES, 7L)*.files
        SchemaMutator.mutants(FILES, 7L)*.id == SchemaMutator.mutants(FILES, 8L)*.id
    }

    void "a breaking mutant changes the element that it names"() {
        when:
        Mutant rename = SchemaMutator.mutants(FILES, 1L).find { it.id == 'rename-field Query.book' }
        Mutant drop = SchemaMutator.mutants(FILES, 1L).find { it.id == 'drop-enum-value Level.HIGH' }

        then:
        rename.files['a.graphqls'].contains('bookRenamed(')
        rename.names*.toString().contains('Query.book')
        !drop.files['a.graphqls'].contains('HIGH')
        drop.files['b.graphqls'] == FILES['b.graphqls']
    }

    void "moving a field into an extension keeps the field"() {
        when:
        Mutant moved = SchemaMutator.mutants(FILES, 1L).find { it.id == 'move-field-to-extension Query.books' }

        then:
        moved.files['a.graphqls'] =~ /(?s)extend type Query.*books/
    }

    void "the manifest of a runner normalizes the order of the arguments only"() {
        expect:
        DriftMutationRunner.normalize('Query.page(offset: Int!, first: Int!): String! -> root ; x') ==
                DriftMutationRunner.normalize('Query.page(first: Int!, offset: Int!): String! -> root ; x')
        DriftMutationRunner.normalize('Query.a: String -> root ; x') != DriftMutationRunner.normalize('Query.b: String -> root ; x')
        DriftMutationRunner.bound('Query.page(a: Int): String -> root', 'Query.page')
        !DriftMutationRunner.bound('Query.pages: String -> root', 'Query.page')
    }

}
