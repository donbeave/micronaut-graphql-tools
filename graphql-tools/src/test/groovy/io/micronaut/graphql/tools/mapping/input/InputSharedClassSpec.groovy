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
package io.micronaut.graphql.tools.mapping.input

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class InputSharedClassSpec extends AbstractTest {

    static final String SPEC_SHARED = "InputSharedClassSpecShared"
    static final String SPEC_UNCOVERED = "InputSharedClassSpecUncovered"
    static final String SPEC_EXCLUDED = "InputSharedClassSpecExcluded"

    @Language("GraphQL")
    static final String SCHEMA_SHARED = """
schema { query: Query mutation: Mutation }
type Query { ping: String }
type Mutation {
  create(input: CreateInput!): String
  rename(input: RenameInput!): String
}
input CreateInput { name: String! }
input RenameInput { id: Int! name: String! }
"""

    @Language("GraphQL")
    static final String SCHEMA_UNCOVERED = """
schema { query: Query mutation: Mutation }
type Query { ping: String }
type Mutation {
  create(input: CreateInput!): String
  rename(input: RenameInput!): String
}
input CreateInput { name: String! }
input RenameInput { name: String! }
"""

    @Language("GraphQL")
    static final String SCHEMA_EXCLUDED = """
schema { query: Query mutation: Mutation }
type Query { ping: String }
type Mutation { create(input: CreateInput!): String }
input CreateInput { name: String! }
"""

    void "one class mapped to two input types binds when each type covers a different subset"() {
        given:
        startContext(SCHEMA_SHARED, SPEC_SHARED)

        when:
        def result = executeQuery('mutation { rename(input: {id: 7, name: "x"}) }')

        then:
        result.errors.isEmpty()
        result.data.rename == '7:x'
        applicationContext.getBean(BindingManifest).diagnostics.findAll { it.contains('unmapped') }.isEmpty()
    }

    void "a property that no type mapped to the class covers is reported once, naming the class and the property"() {
        given:
        startContext(SCHEMA_UNCOVERED, SPEC_UNCOVERED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNMAPPED_INPUT_PROPERTY
        e.cause.unmappedProperties.values()*.value.flatten() == ['id']
        e.cause.unmappedProperties.values()*.key == [SharedDto]
        e.cause.message.contains("mapped class: ${SharedDto.name}, properties: id")
    }

    void "an excluded server-populated property binds without error"() {
        given:
        startContext(SCHEMA_EXCLUDED, SPEC_EXCLUDED)

        when:
        def result = executeQuery('mutation { create(input: {name: "x"}) }')

        then:
        result.errors.isEmpty()
        result.data.create == 'x'
        applicationContext.getBean(BindingManifest).diagnostics.findAll { it.contains('unmapped') }.isEmpty()
    }

    @Requires(property = 'spec.name', pattern = 'InputSharedClassSpec(Shared|Uncovered)')
    @GraphQLRootResolver
    static class Resolver {
        String ping() { 'pong' }

        String create(SharedDto input) { input.name }

        String rename(SharedDto input) { "${input.id}:${input.name}" }
    }

    @Requires(property = 'spec.name', value = SPEC_EXCLUDED)
    @GraphQLRootResolver
    static class ExcludedResolver {
        String ping() { 'pong' }

        String create(ExcludedDto input) { input.name }
    }

    @GraphQLInput
    static class SharedDto {
        String name
        Integer id
    }

    @GraphQLInput
    static class ExcludedDto {
        String name
        @GraphQLIgnore
        String createdBy
    }
}
