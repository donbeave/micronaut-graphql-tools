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
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class RootResolverMappingConflictEnumSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMappingConflictEnumSpec"

    void "attempt map a single enum to a different implementations"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  currentMonth: Month
  nextMonth: Month
}

enum Month {
  JANUARY
  FEBRUARY
  MARCH
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.MAPPING_CONFLICT
        e.cause.message == """[mapping-conflict] Unable to map GraphQL enum `Month` to ${AnotherMonth.name}, as it is already mapped to ${Month.name}.
  GraphQL coordinate: Query.nextMonth (line 8, column 3)
  Mapped class: ${Query.name}
  Mapped method: nextMonth()"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'nextMonth'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'nextMonth()'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        Month currentMonth() {
            return null
        }

        AnotherMonth nextMonth() {
            return null
        }
    }

    @Introspected
    static enum Month {
        JANUARY,
        FEBRUARY,
        MARCH
    }

    @Introspected
    static enum AnotherMonth {
        JANUARY,
        FEBRUARY,
        MARCH
    }

}
