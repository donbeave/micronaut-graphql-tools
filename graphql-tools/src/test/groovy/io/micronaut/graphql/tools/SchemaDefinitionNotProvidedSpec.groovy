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
package io.micronaut.graphql.tools

import io.micronaut.context.exceptions.BeanInstantiationException
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class SchemaDefinitionNotProvidedSpec extends AbstractSpec {

    void "schema definition"() {
        given:
        @Language("GraphQL")
        String schema = """
type Hello {
  hello: String
}
"""

        startContext(schema, null)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.SCHEMA_DEFINITION_NOT_PROVIDED
        e.cause.message == """[schema-definition-not-provided] Schema definition is not set, and the schema has no object type named Query. Define one, or make sure your GraphQL schema contains such definition:
  schema {
    query: Query
    mutation: Mutation
  }"""
    }

}
