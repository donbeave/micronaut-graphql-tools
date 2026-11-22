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

import com.tailrocks.graphql.datetime.LocalDateScalar
import graphql.scalars.ExtendedScalars
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.context.exceptions.BeanInstantiationException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.time.LocalDate
import java.time.OffsetDateTime
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class InputObjectConversionSpec extends AbstractTest {

    static final String SPEC_NAME = "InputObjectConversionSpec"
    static final String SPEC_NAME_READ_ONLY = "InputObjectConversionSpecReadOnly"

    @Language("GraphQL")
    static final String SCHEMA = """
scalar LocalDate
scalar DateTime

schema {
  query: Query
}

type Query {
  register(input: RegistrationInput!): String!
}

input RegistrationInput {
  email: String!
  displayName: String
  birthday: LocalDate
  confirmedAt: DateTime
}
"""

    static final String QUERY = """
{
    register(input: {email: "jane@example.org", displayName: "Jane", birthday: "2000-02-29",
                     confirmedAt: "2026-12-24T10:15:30+02:00"})
}
"""

    void "an input object is converted from its introspection, scalars included"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery(QUERY)

        then:
        result.errors.isEmpty()
        result.data.register == 'jane@example.org (Jane) born 2000-02-29 (LocalDate) confirmed 2026-12-24T10:15:30+02:00'
    }

    void "the application's Jackson configuration does not apply to input conversion"() {
        given: 'a naming strategy that would rename displayName and confirmedAt if it applied'
        startContext(SCHEMA, SPEC_NAME, ['jackson.property-naming-strategy': 'SNAKE_CASE'])

        when:
        def result = executeQuery(QUERY)

        then:
        result.errors.isEmpty()
        result.data.register == 'jane@example.org (Jane) born 2000-02-29 (LocalDate) confirmed 2026-12-24T10:15:30+02:00'
    }

    void "an input field the input class cannot set fails the startup instead of each request"() {
        given:
        startContext(SCHEMA, SPEC_NAME_READ_ONLY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.INPUT_FIELD_NOT_SETTABLE
        e.cause.message.startsWith('[input-field-not-settable] The input field RegistrationInput.displayName cannot be set')
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '.*')
    @Factory
    static class Scalars {
        @Bean
        @Singleton
        GraphQLScalarMapping localDate() {
            return GraphQLScalarMapping.of(LocalDateScalar.create(null, false, null), LocalDate)
        }

        @Bean
        @Singleton
        GraphQLScalarMapping dateTime() {
            return GraphQLScalarMapping.of(ExtendedScalars.DateTime, OffsetDateTime)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String register(RegistrationInput input) {
            return "${input.email} (${input.displayName}) born ${input.birthday} (${input.birthday.class.simpleName}) " +
                    "confirmed ${input.confirmedAt}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_READ_ONLY)
    @Singleton
    @GraphQLRootResolver
    static class ReadOnlyQuery {
        String register(ReadOnlyRegistrationInput input) {
            return input.email
        }
    }

    @GraphQLInput
    static class RegistrationInput {
        String email
        String displayName
        LocalDate birthday
        OffsetDateTime confirmedAt
    }

    @GraphQLInput
    static class ReadOnlyRegistrationInput {
        String email
        LocalDate birthday
        OffsetDateTime confirmedAt

        String getDisplayName() {
            return email
        }
    }

}
