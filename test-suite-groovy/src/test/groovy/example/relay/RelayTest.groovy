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
package example.relay

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

// The Groovy suite runs on the JVM: a Relay connection that is bound to own classes, with strict mode at its default.
class RelayTest extends Specification {

    private static final String PAGE = 'query ($first: Int!, $after: String) {' +
            ' articles(first: $first, after: $after) {' +
            ' totalCount edges { cursor node { id title } }' +
            ' pageInfo { hasNextPage hasPreviousPage startCursor endCursor } } }'

    private static Map<String, Object> page(ApplicationContext context, int first, String after) {
        def result = GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build()
                .execute(ExecutionInput.newExecutionInput().query(PAGE).variables([first: first, after: after]).build())
        assert result.errors.isEmpty(): result.errors.toString()
        return (result.getData() as Map<String, Object>).articles as Map<String, Object>
    }

    // tag::paging[]
    void 'first and after page through the connection'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], RelayFixtures.RELAY)
        List<String> titles = []
        String after = null
        int pages = 0
        boolean more = true

        when:
        while (more) {
            Map<String, Object> connection = page(context, 2, after)
            assert connection.totalCount == 5
            (connection.edges as List<Map<String, Object>>).each { titles << (it.node as Map<String, Object>).title }
            Map<String, Object> pageInfo = connection.pageInfo as Map<String, Object>
            assert pageInfo.hasPreviousPage == (pages > 0)
            after = pageInfo.endCursor
            more = pageInfo.hasNextPage
            pages++
        }

        then:
        pages == 3
        titles == ['Article 1', 'Article 2', 'Article 3', 'Article 4', 'Article 5']

        cleanup:
        context?.close()
    }
    // end::paging[]

    void 'a negative first and a huge first are handled'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], RelayFixtures.RELAY)

        when:
        def result = GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build()
                .execute(ExecutionInput.newExecutionInput().query(PAGE).variables([first: -1]).build())

        then:
        !result.errors.isEmpty()
        (page(context, Integer.MAX_VALUE, null).edges as List).size() == 5

        cleanup:
        context?.close()
    }

    void 'an overflowing cursor is rejected'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], RelayFixtures.RELAY)
        String cursor = Base64.encoder.encodeToString('offset:2147483647'.bytes)

        when:
        def result = GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build()
                .execute(ExecutionInput.newExecutionInput().query(PAGE).variables([first: 2, after: cursor]).build())

        then:
        !result.errors.isEmpty()

        cleanup:
        context?.close()
    }

    void 'a page after the last cursor is empty'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], RelayFixtures.RELAY)

        when:
        String last = (page(context, 5, null).pageInfo as Map<String, Object>).endCursor
        Map<String, Object> connection = page(context, 2, last)
        Map<String, Object> pageInfo = connection.pageInfo as Map<String, Object>

        then:
        connection.edges == []
        pageInfo.hasNextPage == false
        pageInfo.startCursor == null
        pageInfo.endCursor == null

        cleanup:
        context?.close()
    }

}
