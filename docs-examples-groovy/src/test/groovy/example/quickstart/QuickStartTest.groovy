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
package example.quickstart

import graphql.ExecutionResult
import graphql.GraphQL
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class QuickStartTest extends Specification {

    // tag::query[]
    def 'the first query returns the books'() {
        given:
        ApplicationContext context = ApplicationContext.run()
        GraphQL graphQL = context.getBean(GraphQL)

        when:
        ExecutionResult result = graphQL.execute('{ allBooks { id title } }')

        then:
        result.errors.empty
        result.<Map<String, List<Map>>> getData().allBooks.first().title == 'The Fractured Sage'

        cleanup:
        context?.close()
    }
    // end::query[]

}
