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
package io.micronaut.graphql.tools.testing

import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphqlErrorBuilder
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

/** The request, the paths and the assertions of the tester, against an invocation that returns a fixed result. */
class GraphQLTesterSpec extends Specification {

    GraphQLInvocationData seenData
    HttpRequest<?> seenRequest
    ExecutionResult result

    GraphQLTester tester = new GraphQLTester(new GraphQLInvocation() {
        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData data, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            seenData = data
            seenRequest = httpRequest
            Mono.just(result)
        }
    })

    private static ExecutionResult ok(Map data) {
        ExecutionResultImpl.newExecutionResult().data(data).build()
    }

    void "the request passes the document, the operation name and the variables to the invocation"() {
        given:
        result = ok([a: 1])

        when:
        tester.request('query Q($id: ID) { a }').operationName('Q').variable('id', 'x').variables([n: 2]).execute()

        then:
        seenData.query == 'query Q($id: ID) { a }'
        seenData.operationName == 'Q'
        seenData.variables == [id: 'x', n: 2]
    }

    void "the headers and the context values travel on the synthetic request"() {
        given:
        result = ok([a: 1])

        when:
        tester.request('{ a }').header('X-User', 'alice').context('tenant', 't1').execute()

        then:
        seenRequest.headers.get('X-User') == 'alice'
        seenRequest.getAttribute(GraphQLTester.CONTEXT_ATTRIBUTE, Map).get() == [tenant: 't1']
    }

    void "a call without a context value sets no attribute"() {
        given:
        result = ok([a: 1])

        when:
        tester.execute('{ a }')

        then:
        seenRequest.getAttribute(GraphQLTester.CONTEXT_ATTRIBUTE).empty
    }

    void "a path reads fields and list indexes"() {
        given:
        result = ok([book: [title: 'Dune', authors: [[name: 'Frank'], [name: null]]]])
        def response = tester.execute('{ book }')

        expect:
        response.path('book.title').get() == 'Dune'
        response.path('book.authors[0].name', String).get() == 'Frank'
        response.path('book.authors[1].name').empty
        response.path('book.authors[2]').empty
        response.path('missing.title').empty
        response.assertNoErrors()
                .assertPath('book.title', 'Dune')
                .assertPath('book.authors[0]', [name: 'Frank'])
                .assertPathNull('book.authors[1].name')
                .assertPathPresent('book.authors[0].name')
    }

    void "an assertion on a path fails with the response in the message"() {
        given:
        result = ok([book: [title: 'Dune']])
        def response = tester.execute('{ book }')

        when:
        response.assertPath('book.title', 'Emma')

        then:
        def e = thrown(AssertionError)
        e.message.contains('expected book.title to be Emma, but it is Dune')
        e.message.contains('data={book={title=Dune}}')

        when:
        response.assertPath('book.year', 1965)

        then:
        def missing = thrown(AssertionError)
        missing.message.contains('the path does not exist')

        when:
        response.assertPathNull('book.title')

        then:
        thrown(AssertionError)

        when:
        response.assertPathPresent('book.year')

        then:
        thrown(AssertionError)

        when:
        response.path('book.title', Integer)

        then:
        thrown(AssertionError)
    }

    void "the error assertions match the message, the path and the extensions"() {
        given:
        result = ExecutionResultImpl.newExecutionResult().data(null)
                .addError(GraphqlErrorBuilder.newError().message('Boom: bad input').path(['books', 0, 'title']).extensions([code: 'BAD']).build())
                .build()
        def response = tester.execute('{ books }')

        expect:
        response.data == null
        response.assertErrorCount(1)
                .assertErrorMessage('bad input')
                .assertErrorPath('books[0].title')
                .assertErrorExtension('code', 'BAD')
        response.path('books').empty
    }

    void "the error assertions fail when nothing matches"() {
        given:
        result = ExecutionResultImpl.newExecutionResult().data(null)
                .addError(GraphqlErrorBuilder.newError().message('Boom').build())
                .build()
        def response = tester.execute('{ books }')

        when:
        response.assertNoErrors()

        then:
        thrown(AssertionError)

        when:
        response.assertErrorCount(2)

        then:
        thrown(AssertionError)

        when:
        response.assertErrorMessage('other')

        then:
        thrown(AssertionError)

        when:
        response.assertErrorPath('books')

        then:
        thrown(AssertionError)

        when:
        response.assertErrorExtension('code', 'BAD')

        then:
        thrown(AssertionError)
    }

    void "the extensions of the response are readable"() {
        given:
        result = ExecutionResultImpl.newExecutionResult().data([a: 1]).extensions([trace: 'on']).build()
        def response = tester.execute('{ a }')

        expect:
        response.extensions == [trace: 'on']
        response.assertExtension('trace', 'on')

        when:
        response.assertExtension('trace', 'off')

        then:
        thrown(AssertionError)
    }

    void "an unclosed index is rejected"() {
        given:
        result = ok([a: [1]])

        when:
        tester.execute('{ a }').path('a[0')

        then:
        thrown(IllegalArgumentException)
    }

}
