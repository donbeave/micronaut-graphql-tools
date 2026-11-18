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

import io.micronaut.graphql.tools.binding.internal.manifest.ManifestRenderer
import io.micronaut.graphql.tools.binding.internal.plan.BindingPlan
import io.micronaut.graphql.tools.binding.internal.plan.BindingPlan.Entry
import spock.lang.Specification

class ManifestRendererSpec extends Specification {

    void "a plan renders as the header and the sorted lines"() {
        given:
        def plan = new BindingPlan([
                new Entry('Query.book(id: ID!): Book', 'root', { 'a.R#book(java.lang.String)' }, { 'a.Book' }, { 'a.Book' }),
                Entry.of('Book', 'object', { 'a.Book' }),
                Entry.of('Genre.FICTION', 'enumValue', { 'a.Genre#FICTION' }),
        ], [ManifestRenderer.diagnostic('unreachable', 'Orphan')], [ManifestRenderer.ignored('a.R#hidden()')])

        when:
        def rendered = ManifestRenderer.render(plan, ['# binder: test'])

        then:
        rendered.header() == ['# graphql-binding-manifest 2', '# binder: test']
        rendered.lines() == [
                '!unreachable Orphan',
                'Book -> object ; a.Book ; - ; -',
                'Genre.FICTION -> enumValue ; a.Genre#FICTION ; - ; -',
                'Query.book(id: ID!): Book -> root ; a.R#book(java.lang.String) ; a.Book ; a.Book',
                '~ignored a.R#hidden()',
        ]
    }

    void "the texts of an entry are read when the plan is rendered, not before"() {
        given:
        def reads = 0
        def plan = new BindingPlan([new Entry('T', 'object', { reads++; 'a.T' }, { reads++; '-' }, { reads++; '-' })], [], [])

        expect:
        reads == 0

        when:
        ManifestRenderer.render(plan, [])

        then:
        reads == 3
    }

    void "a part that holds a separator cannot be written"() {
        when:
        action()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot write the binding manifest: "' + part + '" contains a separator ("->" or ";").'

        where:
        part     | action
        'a ; b'  | { ManifestRenderer.diagnostic('unreachable', 'a ; b') }
        'a -> b'  | { ManifestRenderer.ignored('a -> b') }
        'a ; b'  | { ManifestRenderer.render(new BindingPlan([Entry.of('T', 'object', { 'a ; b' })], [], []), []) }
    }

    void "the text is ASCII: a character outside printable ASCII and the backslash are written as backslash-u and four hex digits"() {
        expect:
        ManifestRenderer.ignored(part) == '~ignored ' + escaped

        where:
        part         | escaped
        'a.B#plain'  | 'a.B#plain'
        'a.\u00c9#x' | 'a.\\u00c9#x'
        'a\nb'       | 'a\\u000ab'
        'a\\b'       | 'a\\u005cb'
        'a\u20acb'   | 'a\\u20acb'
    }

    void "the header has the three lines after the format line, in this order"() {
        expect:
        ManifestRenderer.header('1.0.0', 'unknown', 'ab') == [
                '# binder: micronaut-graphql-tools 1.0.0', '# graphql-java: unknown', '# schema-sha256: ab']
        ManifestRenderer.FORMAT == '# graphql-binding-manifest ' + ManifestRenderer.VERSION
    }

    void "a plan does not change when the lists it was built from do"() {
        given:
        def entries = [Entry.of('T', 'object', { 'a.T' })]
        def plan = new BindingPlan(entries, [], [])

        when:
        entries.clear()

        then:
        plan.entries().size() == 1
    }
}
