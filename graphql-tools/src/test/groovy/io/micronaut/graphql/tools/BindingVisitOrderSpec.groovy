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

import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

/**
 * The binding visits the schema depth first, in the order of the fields and of the members: the type that two fields
 * reach with different classes is bound to the class of the first field that reaches it, and the later field is the one
 * that fails. The expected values were produced by the recursive walk that the work stack replaced.
 */
class BindingVisitOrderSpec extends AbstractTest {

    static final String SPEC_NAME = "BindingVisitOrderSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  deep: OrdA
  wide: OrdB
  pair: OrdPair
  union: OrdUnion
  node: OrdNode
}

type OrdA {
  inner: OrdC
}

type OrdC {
  shared: OrdShared
}

type OrdB {
  shared: OrdShared
}

type OrdShared {
  name: String
}

type OrdPair {
  first: OrdSame
  second: OrdSame
}

type OrdSame {
  name: String
}

union OrdUnion = OrdM1 | OrdM2 | OrdM3

type OrdM1 {
  missingOne: String
}

type OrdM2 {
  name: String
}

type OrdM3 {
  missingThree: String
}

interface OrdNode {
  id: ID
}

type OrdN1 implements OrdNode {
  id: ID
  missingOne: String
}

type OrdN2 implements OrdNode {
  id: ID
}

type OrdN3 implements OrdNode {
  id: ID
  missingThree: String
}
"""

    void "the type that two fields reach with different classes fails on the later field, in depth first order"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.BINDING_FAILURES

        SchemaBindingException failures = e.cause
        failures.failures*.coordinate == EXPECTED_COORDINATES
        failures.failures*.message.findAll { it.contains('Unable to map') }*.readLines()*.first() == EXPECTED_CONFLICTS
    }

    static final List<String> EXPECTED_COORDINATES = [
            'OrdB.shared', 'OrdM1.missingOne', 'OrdN1.missingOne', 'OrdPair.second', 'Query.node', 'Query.union'
    ]

    static final List<String> EXPECTED_CONFLICTS = [
            '[mapping-conflict] Unable to map GraphQL type `OrdShared` to ' + OrdSharedTwo.name + ', as it is already mapped to '
                    + OrdSharedOne.name + '.',
            '[mapping-conflict] Unable to map GraphQL type `OrdSame` to ' + OrdSameTwo.name + ', as it is already mapped to '
                    + OrdSameOne.name + '.'
    ]

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        OrdA deep() { null }

        OrdB wide() { null }

        OrdPair pair() { null }

        OrdUnionMarker union() { null }

        OrdNodeMarker node() { null }
    }

    @GraphQLType
    static class OrdA {
        OrdC inner
    }

    @GraphQLType
    static class OrdC {
        OrdSharedOne shared
    }

    @GraphQLType
    static class OrdB {
        OrdSharedTwo shared
    }

    @GraphQLType
    static class OrdSharedOne {
        String name
    }

    @GraphQLType
    static class OrdSharedTwo {
        String name
    }

    @GraphQLType
    static class OrdPair {
        OrdSameOne first
        OrdSameTwo second
    }

    @GraphQLType
    static class OrdSameOne {
        String name
    }

    @GraphQLType
    static class OrdSameTwo {
        String name
    }

    interface OrdUnionMarker {
    }

    @GraphQLType
    static class OrdM1 implements OrdUnionMarker {
        String other
    }

    @GraphQLType
    static class OrdM3 implements OrdUnionMarker {
        String other
    }

    interface OrdNodeMarker {
        String getId()
    }

    @GraphQLType
    static class OrdN1 implements OrdNodeMarker {
        String id
    }

    @GraphQLType
    static class OrdN3 implements OrdNodeMarker {
        String id
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        GraphQLSchemaMappingDictionaryCustomizer dictionary() {
            return { SchemaMappingDictionary dictionary ->
                dictionary.registerType("OrdM1", OrdM1)
                dictionary.registerType("OrdM3", OrdM3)
                dictionary.registerType("OrdN1", OrdN1)
                dictionary.registerType("OrdN3", OrdN3)
            } as GraphQLSchemaMappingDictionaryCustomizer
        }
    }

}
