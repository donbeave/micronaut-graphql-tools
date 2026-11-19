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

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import org.intellij.lang.annotations.Language

class RootResolverMultipleMethodsFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMultipleMethodsFoundSpec"

    @Language("GraphQL")
    static String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

    void "multiple methods detected for single field"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """[multiple-methods-found] Found multiple methods for one GraphQL field.
  GraphQL coordinate: Query.hello (line 7, column 3)
  Methods: 
  1) ${Query.name} hello()
  2) ${Query.name} hello(${DataFetchingEnvironment.name} env)
  Fix: if one bean is meant to override another, annotate the overriding bean with @Replaces(<overridden bean>.class) ('@Requires(env = ...)' alone does not drop a bean); if the methods serve different operation types, annotate each with the @GraphQLSchemaCoordinate("<Operation>.<field>") of its own operation type, for this field @GraphQLSchemaCoordinate("Query.hello") on one of them (two methods with the same coordinate still conflict)."""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.mappingContext.mappedClass == null
        e.cause.mappingContext.mappedMethod == null
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return null
        }

        String hello(DataFetchingEnvironment env) {
            return null
        }
    }

}
