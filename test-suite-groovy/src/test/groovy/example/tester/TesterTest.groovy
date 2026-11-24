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
package example.tester

import io.micronaut.graphql.tools.testing.GraphQLTester
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

// The Groovy suite runs on the JVM, and so does the tester: it is test-time code.
@MicronautTest(environments = TesterFixtures.TESTER)
class TesterTest extends Specification {

    @Inject
    GraphQLTester tester

    // tag::data[]
    void 'a query returns its data'() {
        when:
        def response = tester.execute('{ testerBook(id: "b1") { id title } }')

        then:
        response.assertNoErrors()
                .assertPath('testerBook.title', 'Book b1')
                .assertPath('testerBook', [id: 'b1', title: 'Book b1'])
        response.path('testerBook.id', String).get() == 'b1'
    }
    // end::data[]

    void 'a null field is null in the data'() {
        expect:
        tester.execute('{ testerBook(id: "none") { id } }').assertNoErrors().assertPathNull('testerBook')
    }

    // tag::variables[]
    void 'variables and the operation name are sent'() {
        expect:
        tester.request('query Pages($count: Int!) { testerPages(count: $count) } query Other { testerPages(count: 1) }')
                .operationName('Pages')
                .variable('count', 21)
                .execute()
                .assertNoErrors()
                .assertPath('testerPages', 42)
    }
    // end::variables[]

    // tag::errors[]
    void 'an exception of a resolver is an error of its field'() {
        when:
        def response = tester.execute('{ testerFailing }')

        then:
        response.assertErrorCount(1).assertErrorPath('testerFailing').assertErrorExtension('code', 'INTERNAL_ERROR')
        response.assertPathNull('testerFailing')
        response.errors.size() == 1
    }
    // end::errors[]

    void 'a variable that does not bind is an error of the response'() {
        when:
        def response = tester.request('query($count: Int!) { testerPages(count: $count) }')
                .variable('count', 'many')
                .execute()

        then:
        response.assertErrorCount(1).assertErrorMessage('count')
        response.data == null
    }

    void 'a missing variable and an unknown field are errors'() {
        expect:
        tester.execute('query($count: Int!) { testerPages(count: $count) }').assertErrorCount(1).assertErrorMessage('count')
        tester.execute('{ nothing }').assertErrorCount(1).assertErrorMessage('nothing')
    }

    // tag::context[]
    void 'a context value reaches the resolver'() {
        expect:
        tester.request('{ testerWhoAmI }').context('tester.user', 'alice').execute()
                .assertNoErrors()
                .assertPath('testerWhoAmI', 'alice')
    }
    // end::context[]

    void 'a missing context value is an error of the field'() {
        expect:
        tester.execute('{ testerWhoAmI }').assertErrorCount(1).assertErrorPath('testerWhoAmI')
    }

    void 'an assertion that does not hold is an assertion error'() {
        given:
        def response = tester.execute('{ testerPages(count: 1) }')

        when:
        response.assertPath('testerPages', 3)

        then:
        thrown(AssertionError)

        when:
        response.assertErrorCount(1)

        then:
        thrown(AssertionError)
    }

}
