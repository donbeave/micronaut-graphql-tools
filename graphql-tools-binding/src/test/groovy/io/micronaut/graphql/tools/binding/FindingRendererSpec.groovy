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

import io.micronaut.graphql.tools.binding.internal.finding.Finding
import io.micronaut.graphql.tools.binding.internal.finding.FindingRenderer
import io.micronaut.graphql.tools.binding.internal.finding.SchemaLocation
import spock.lang.Specification

class FindingRendererSpec extends Specification {

    void "a finding with every part renders four lines"() {
        given:
        def finding = new Finding('argument-mismatch', 'Query.books',
                'parameter `limit` binds no argument of Query.books (arguments: genre)',
                'example.BookQuery#books(example.GenreEnum,int)', null,
                new SchemaLocation('/work/app/schema.graphqls', 3, 3),
                'rename the parameter to an argument name')

        expect:
        FindingRenderer.render(finding) == '''\
[argument-mismatch] Query.books: parameter `limit` binds no argument of Query.books (arguments: genre).
  at example.BookQuery#books(example.GenreEnum,int)
  schema /work/app/schema.graphqls:3:3
  fix: rename the parameter to an argument name'''
    }

    void "an inherited member names the contract that declares it"() {
        expect:
        FindingRenderer.render(new Finding('unbound-resolver-method', 'Book.title', 'binds nothing.',
                'a.B#m()', 'a.Contract', null, null)) == '''\
[unbound-resolver-method] Book.title: binds nothing.
  at a.B#m() (declared in a.Contract)'''
    }

    void "a finding without a coordinate prints a dash and a sentence ends with one period"() {
        expect:
        FindingRenderer.render(Finding.of('schema-invalid', null, 'the schema has a duplicate type. ')) ==
                '[schema-invalid] -: the schema has a duplicate type.'
    }

    void "the same finding renders the same text, which is what compile time and startup rely on"() {
        given:
        def first = Finding.of('unbound-enum-constant', 'Genre', 'constant POETRY has no value')
        def second = Finding.of('unbound-enum-constant', 'Genre', 'constant POETRY has no value')

        expect:
        FindingRenderer.render(first) == FindingRenderer.render(second)
    }
}
