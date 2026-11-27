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
package io.micronaut.graphql.tools.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class GraphQLCodecSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared GraphQLCodec codec = context.getBean(GraphQLCodec)

    private static byte[] json(String text) {
        text.getBytes(StandardCharsets.UTF_8)
    }

    private String encoded(GraphQLRequest<?> request) {
        new String(codec.encode(request), StandardCharsets.UTF_8)
    }

    void "a response with data decodes the typed data"() {
        when:
        def response = codec.decode(json('{"data":{"title":"Dune","pages":412,"id":"9007199254740993"}}'), Argument.of(Models.Book))

        then:
        response.hasData()
        response.data() == new Models.Book('Dune', 412, 9007199254740993L)
        response.errors().empty
        response.extensions().isEmpty()
        response.httpResponse() == null
        response.dataOrThrow() == response.data()
    }

    void "a response with errors and no data has no data entry"() {
        when:
        def response = codec.decode(json('''{"errors":[{"message":"Bad","locations":[{"line":2,"column":3}],"path":["book",0,"title"],
            "extensions":{"code":"BAD","n":1}}]}'''), Argument.of(Models.Book))

        then:
        !response.hasData()
        response.data() == null
        response.errors() == [new GraphQLError('Bad', [new GraphQLError.Location(2, 3)], ['book', 0, 'title'], [code: 'BAD', n: 1])]
    }

    void "a response with data and errors keeps both"() {
        when:
        def response = codec.decode(json('{"errors":[{"message":"Partial"}],"data":{"title":"Dune","pages":1,"id":1}}'), Argument.of(Models.Book))

        then:
        response.hasData()
        response.data().title() == 'Dune'
        response.errors()*.message() == ['Partial']
        response.errors()[0].locations().empty
        response.errors()[0].path().empty
        response.errors()[0].extensions().isEmpty()
    }

    void "a response with neither data nor errors is empty"() {
        when:
        def response = codec.decode(json('{"extensions":{"tracing":true}}'), Argument.of(Models.Book))

        then:
        !response.hasData()
        response.data() == null
        response.errors().empty
        response.extensions() == [tracing: true]
    }

    void "data null is told from absent data"() {
        when:
        def absent = codec.decode(json('{}'), Argument.of(Models.Book))
        def explicit = codec.decode(json('{"data":null}'), Argument.of(Models.Book))
        def nullErrors = codec.decode(json('{"data":null,"errors":null}'), Argument.of(Models.Book))

        then:
        !absent.hasData()
        explicit.hasData()
        explicit.data() == null
        nullErrors.hasData()
        nullErrors.errors().empty
    }

    void "dataOrThrow throws on errors, even with partial data, and on a missing data entry"() {
        when:
        codec.decode(json('{"errors":[{"message":"One"},{"message":"Two"}],"data":{"title":"x","pages":1,"id":1}}'), Argument.of(Models.Book)).dataOrThrow()

        then:
        def errors = thrown(io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException)
        errors.code == 'graphql-errors'
        errors.message == '[graphql-errors] One (and 1 more)'
        errors.errors*.message() == ['One', 'Two']
        errors.response.hasData()

        when:
        codec.decode(json('{}'), Argument.of(Models.Book)).dataOrThrow()

        then:
        def noData = thrown(GraphQLClientException)
        noData.code == 'no-data'
        noData.message.startsWith('[no-data] ')
    }

    void "data decodes as a list, a map and a generic type"() {
        expect:
        codec.decode(json('{"data":[{"title":"A","pages":1,"id":1},{"title":"B","pages":2,"id":2}]}'),
                Argument.listOf(Models.Book)).data()*.title() == ['A', 'B']
        codec.decode(json('{"data":{"book":{"title":"A"}}}'), Argument.mapOf(String, Object)).data() == [book: [title: 'A']]
    }

    void "unknown entries and unknown properties are ignored"() {
        when:
        def response = codec.decode(json('{"unknown":[1,{"a":2}],"data":{"title":"A","pages":1,"id":1,"extra":true},"errors":[{"message":"m","other":{}}]}'),
                Argument.of(Models.Book))

        then:
        response.data().title() == 'A'
        response.errors()*.message() == ['m']
    }

    void "a polymorphic result is chosen by its type name"() {
        expect:
        codec.decode(json('{"data":{"__typename":"Dog","name":"Rex"}}'), Argument.of(Models.Pet)).data() == new Models.Dog('Rex')
        codec.decode(json('{"data":[{"__typename":"Cat","name":"Tom"},{"name":"Rex","__typename":"Dog"}]}'),
                Argument.listOf(Models.Pet)).data() == [new Models.Cat('Tom'), new Models.Dog('Rex')]
    }

    void "unwrap decodes the value of the single root field"() {
        when:
        def response = codec.decodeUnwrapped(json('{"data":{"book":{"title":"Dune","pages":412,"id":7}}}'), Argument.of(Models.Book))

        then:
        response.hasData()
        response.data() == new Models.Book('Dune', 412, 7L)
    }

    void "unwrap of a null root field keeps data presence and a null value"() {
        when:
        def response = codec.decodeUnwrapped(json('{"data":{"book":null}}'), Argument.of(Models.Book))
        def nullData = codec.decodeUnwrapped(json('{"data":null,"errors":[{"message":"x"}]}'), Argument.of(Models.Book))
        def absent = codec.decodeUnwrapped(json('{"errors":[{"message":"x"}]}'), Argument.of(Models.Book))

        then:
        response.hasData()
        response.data() == null
        response.dataOrThrow() == null
        nullData.hasData()
        nullData.errors().size() == 1
        !absent.hasData()
    }

    void "unwrap decodes a list, a polymorphic value and keeps errors and extensions"() {
        when:
        def list = codec.decodeUnwrapped(json('{"data":{"books":[{"title":"A","pages":1,"id":1}]}}'), Argument.listOf(Models.Book))
        def pet = codec.decodeUnwrapped(json('{"data":{"pet":{"__typename":"Cat","name":"Tom"}}}'), Argument.of(Models.Pet))
        def both = codec.decodeUnwrapped(json('{"errors":[{"message":"x"}],"data":{"book":null},"extensions":{"a":1}}'), Argument.of(Models.Book))

        then:
        list.data()*.title() == ['A']
        pet.data() == new Models.Cat('Tom')
        both.data() == null
        both.errors().size() == 1
        both.extensions() == [a: 1]
    }

    void "unwrap rejects data with no field or several fields"() {
        when:
        codec.decodeUnwrapped(json(body), Argument.of(Models.Book))

        then:
        def e = thrown(GraphQLClientException)
        e.code == 'decode-error'
        e.message.contains(reason)

        where:
        body                                                                                | reason
        '{"data":{}}'                                                                        | 'no root field'
        '{"data":{"a":{"title":"x","pages":1,"id":1},"b":{"title":"y","pages":1,"id":1}}}'  | 'more than one root field'
    }

    void "a body that is not a response fails with the decode error"() {
        when:
        codec.decode(json(body), Argument.of(Models.Book))

        then:
        thrown(GraphQLClientException)

        where:
        body << ['[]', 'not json', '{"data":{"title":5.5.5}}']
    }

    void "a minimal request encodes the document only"() {
        expect:
        encoded(GraphQLRequest.builder().document('{ a }').build()) == '{"query":"{ a }"}'
    }

    void "a full request encodes every key"() {
        given:
        def request = GraphQLRequest.builder(Models.Book)
                .document('query Q($f: Filter) { book(f: $f) { title } }')
                .operationName('Q')
                .variable('f', [name: 'x', tags: ['a', 'b']])
                .variable('n', null)
                .extension('persistedQuery', [version: 1])
                .build()

        expect:
        encoded(request) ==
                '{"query":"query Q($f: Filter) { book(f: $f) { title } }","operationName":"Q",' +
                '"variables":{"f":{"name":"x","tags":["a","b"]},"n":null},"extensions":{"persistedQuery":{"version":1}}}'
    }

    void "a variable that is a serializable bean is encoded"() {
        expect:
        encoded(GraphQLRequest.builder().document('{ a }').variable('f', new Models.Filter('x', ['t'])).build()) ==
                '{"query":"{ a }","variables":{"f":{"name":"x","tags":["t"]}}}'
    }
}
