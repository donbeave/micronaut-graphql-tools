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
package io.micronaut.graphql.tools.mapping.resolver.root

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

class RootResolverNullArgumentsSpec extends AbstractSpec {

    static final String SPEC_NAME = "RootResolverNullArgumentsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello(input: HelloInput, inputList: [HelloInput], inputString: String, inputStringList: [String]): String
}

input HelloInput {
  firstName: String
  lastName: String
}
"""

    void "null arguments successfully passed"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    hello
}
""")

        then:
        result.errors.isEmpty()
        result.data.hello == null

        when:
        result = executeQuery("""
{
    hello(input: null, inputList: null, inputString: null, inputStringList: null)
}
""")

        then:
        result.errors.isEmpty()
        result.data.hello == null
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello(HelloInput input, List<HelloInput> inputList, String inputString, List<String> inputStringList) {
            assert input == null
            assert inputList == null
            assert inputString == null
            assert inputStringList == null
            return null
        }
    }

    @GraphQLInput
    static class HelloInput {
        String firstName
        String lastName
    }

}
