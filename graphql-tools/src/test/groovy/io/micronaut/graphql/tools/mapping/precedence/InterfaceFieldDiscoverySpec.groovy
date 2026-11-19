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
package io.micronaut.graphql.tools.mapping.precedence

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class InterfaceFieldDiscoverySpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceFieldDiscoverySpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  sections: [Section!]!
}

interface Section {
  title: String!
  extra: Extra!
}

type TextSection implements Section {
  title: String!
  extra: Extra!
}

interface Labeled {
  label: String!
}

type Extra implements Labeled {
  code: String!
  label: String!
}
"""

    void "an interface field of a type that only the binding of another interface field reaches is checked"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'resolver-first'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.FIELD_NOT_BOUND
        e.cause.message.contains('GraphQL coordinate: Extra.label')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        List<Section> sections() {
            return [new TextSection(title: 'Notes', extra: new Extra(code: 'x1'))]
        }
    }

    static interface Section {
        String getTitle()
    }

    @GraphQLType
    static class TextSection implements Section {
        String title
        Extra extra
    }

    @GraphQLType
    static class Extra {
        String code
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Section.class)
    static class SectionResolver {
        Extra extra(Section section) {
            return new Extra(code: "x1")
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", TextSection.class)
        }
    }

}
