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
package io.micronaut.graphql.tools.mapping.returntype

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class RawWrapperReturnTypeSpec extends AbstractTest {

    static final String SPEC_NAME_OPTIONAL = "RawWrapperReturnTypeSpecOptional"
    static final String SPEC_NAME_STAGE = "RawWrapperReturnTypeSpecStage"

    void "a raw Optional fails at startup with the dedicated message, not with Object as the mapped class"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  value: String
}
"""
        startContext(schema, SPEC_NAME_OPTIONAL)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNSUPPORTED_RETURN_TYPE
        e.cause.message.startsWith("[unsupported-return-type] The return type of the method or property is not supported for this field: " +
                "java.util.Optional must declare the type it wraps.")
        e.cause.message.contains("GraphQL coordinate: Query.value")
    }

    void "a raw CompletionStage fails at startup with the dedicated message, not with Object as the mapped class"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  value: String
}
"""
        startContext(schema, SPEC_NAME_STAGE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNSUPPORTED_RETURN_TYPE
        e.cause.message.startsWith("[unsupported-return-type] The return type of the method or property is not supported for this field: " +
                "java.util.concurrent.CompletionStage must declare the type it wraps.")
        e.cause.message.contains("GraphQL coordinate: Query.value")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OPTIONAL)
    @GraphQLRootResolver
    static class OptionalQuery {
        Optional value() {
            return Optional.empty()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_STAGE)
    @GraphQLRootResolver
    static class StageQuery {
        CompletionStage value() {
            return CompletableFuture.completedFuture('value')
        }
    }

}
