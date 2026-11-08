package io.micronaut.graphql.tools.mapping.input

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.tailrocks.graphql.datetime.LocalDateScalar
import graphql.scalars.ExtendedScalars
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.GraphQLScalarMapping
import io.micronaut.graphql.tools.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jackson.modules.BeanIntrospectionModule
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Arguments and input objects are converted with a private {@link ObjectMapper} unless the application provides one
 * named {@value GraphQLToolsConfiguration#INPUT_OBJECT_MAPPER}. The private mapper keeps Jackson's default
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=true}.
 */
class InputObjectMapperSpec extends AbstractTest {

    static final String SPEC_NAME = "InputObjectMapperSpec"
    static final String SPEC_NAME_CUSTOM = "InputObjectMapperSpecCustom"
    static final String SPEC_NAME_README = "InputObjectMapperSpecReadme"
    static final String SPEC_NAME_READ_ONLY = "InputObjectMapperSpecReadOnly"

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

    void "the default mapper converts input objects"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery(QUERY)

        then:
        result.errors.isEmpty()
        result.data.register == 'jane@example.org (Jane) born 2000-02-29 (LocalDate) confirmed 2026-12-24T10:15:30+02:00'
    }

    void "the application's default ObjectMapper configuration does not apply to input conversion"() {
        given: 'a naming strategy that would rename displayName and confirmedAt if it applied'
        startContext(SCHEMA, SPEC_NAME, ['jackson.property-naming-strategy': 'SNAKE_CASE'])

        when:
        def result = executeQuery(QUERY)

        then:
        result.errors.isEmpty()
        result.data.register == 'jane@example.org (Jane) born 2000-02-29 (LocalDate) confirmed 2026-12-24T10:15:30+02:00'
    }

    void "an ObjectMapper bean named graphql-tools is used for input conversion"() {
        given:
        startContext(SCHEMA, SPEC_NAME_CUSTOM)

        when:
        def result = executeQuery(QUERY)

        then: 'its String deserializer upper-cases, and scalar values still reach the input class unchanged'
        result.errors.isEmpty()
        result.data.register == 'JANE@EXAMPLE.ORG (JANE) born 2000-02-29 (LocalDate) confirmed 2026-12-24T10:15:30+02:00'
    }

    void "the library works on a copy of the named ObjectMapper"() {
        given:
        startContext(SCHEMA, SPEC_NAME_CUSTOM)

        when:
        graphQLBean
        ObjectMapper mapper = applicationContext.getBean(ObjectMapper,
                Qualifiers.byName(GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER))

        then:
        mapper.registeredModuleIds == ['upper-case-strings'] as Set
    }

    void "with the mapper the README recommends, JavaTimeModule does not alter scalar values"() {
        given:
        startContext(SCHEMA, SPEC_NAME_README)

        when:
        def result = executeQuery(QUERY)

        then: 'a JSR-310 round trip would turn +02:00 into 08:15:30Z; the scalar pass-through keeps the parsed instance'
        result.errors.isEmpty()
        result.data.register == 'jane@example.org (Jane) born 2000-02-29 (LocalDate) confirmed 2026-12-24T10:15:30+02:00'
    }

    void "the default mapper fails a request that sends an input field the input class cannot set"() {
        given:
        startContext(SCHEMA, SPEC_NAME_READ_ONLY)

        when:
        def result = executeQuery(QUERY)

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('Unrecognized field "displayName"')
        result.data == null
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

    @Requires(property = 'spec.name', value = SPEC_NAME_CUSTOM)
    @Factory
    static class Mappers {
        @Bean
        @Singleton
        @Named(GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER)
        ObjectMapper graphQLToolsObjectMapper() {
            // no JSR-310 module: LocalDate and OffsetDateTime values still reach the input class
            return new ObjectMapper().registerModule(new SimpleModule('upper-case-strings')
                    .addDeserializer(String, new UpperCaseDeserializer()))
        }
    }

    static class UpperCaseDeserializer extends StdDeserializer<String> {
        UpperCaseDeserializer() {
            super(String)
        }

        @Override
        String deserialize(JsonParser parser, DeserializationContext context) {
            return parser.valueAsString.toUpperCase()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_README)
    @Factory
    static class ReadmeMappers {
        @Bean
        @Singleton
        @Named(GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER)
        ObjectMapper graphQLToolsObjectMapper() {
            return new ObjectMapper()
                    .registerModule(new BeanIntrospectionModule())
                    .registerModule(new JavaTimeModule())
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(Custom|Readme)?')
    @GraphQLRootResolver
    static class Query {
        String register(RegistrationInput input) {
            return "${input.email} (${input.displayName}) born ${input.birthday} (${input.birthday.class.simpleName}) " +
                    "confirmed ${input.confirmedAt}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_READ_ONLY)
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

        // read-only: the binding finds the property, but Jackson cannot set it
        String getDisplayName() {
            return email
        }
    }

}
