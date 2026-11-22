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
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class RootResolverMethodNotFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMethodNotFoundSpec"

    void "Query's method not found"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.FIELD_NOT_BOUND
        e.cause.message == """[field-not-bound] The method `hello` not found in any root resolvers: [${Query1.name}, ${Query2.name}].
  GraphQL coordinate: Query.hello (line 7, column 3)"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.methodName == 'hello'
    }

    void "Mutation's method not found"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  mutation: Mutation
}

type Mutation {
  hello: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.FIELD_NOT_BOUND
        e.cause.message == """[field-not-bound] The method `hello` not found in any root resolvers: [${Query1.name}, ${Query2.name}].
  GraphQL coordinate: Mutation.hello (line 7, column 3)"""
        e.cause.mappingContext.graphQlObjectType == 'Mutation'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.methodName == 'hello'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query1 {
        String test1() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query2 {
        String test2() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Mutation {
    }

}
