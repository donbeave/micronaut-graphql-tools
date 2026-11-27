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

import io.micronaut.graphql.tools.codegen.Options
import spock.lang.Specification
import spock.lang.Unroll

class NodeTextSpec extends Specification {

    @Unroll
    def "the text '#text' is escaped once for a comment"() {
        expect:
        NodeText.escape(text) == escaped

        where:
        text                       | escaped
        'plain text.'              | 'plain text.'
        'ends */ here'             | 'ends *&#47; here'
        'opens /* nested'          | 'opens /&#42; nested'
        '/**/'                     | '/&#42;*&#47;'
        'a **/ b'                  | 'a **&#47; b'
        '\\u002A\\u002F'           | '&#92;u002A&#92;u002F'
        'a < b & c'                | 'a &lt; b &amp; c'
        '{@link X} @param'         | '{&#64;link X} &#64;param'
        'one\r\ntwo\rthree\nfour'  | 'one\ntwo\nthree\nfour'
        ''                         | ''
    }

    def "an escaped description never holds a comment terminator, an opener, a unicode escape or a tag"() {
        given:
        def hostile = '*/ /* \\u002A/ {@code} @author <b> &amp; **/ /*/'

        expect:
        def escaped = NodeText.escape(hostile)
        !escaped.contains('*/')
        !escaped.contains('/*')
        !escaped.contains('\\')
        !escaped.contains('@')
        !escaped.contains('<')
    }

    def "a null text stays null"() {
        expect:
        NodeText.escape(null) == null
    }

    def "a description in the IR is escaped"() {
        when:
        def result = IrBuilder.fromSources([new SchemaSource('s.graphqls',
                'type Query { q: Int @deprecated(reason: "no */ more") }\n"""Ends */ and \\u0041 {@link X}"""\ntype A { x: Int }')],
                Options.builder().packageName('p').build())

        then:
        result.diagnostics().empty
        result.ir().models()[0].docs().description() == 'Ends *&#47; and &#92;u0041 {&#64;link X}'
        result.ir().contracts()[0].methods()[0].docs().deprecation().reason() == 'no *&#47; more'
    }
}
