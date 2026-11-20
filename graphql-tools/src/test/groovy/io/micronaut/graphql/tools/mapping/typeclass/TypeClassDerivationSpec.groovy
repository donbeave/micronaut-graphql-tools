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
package io.micronaut.graphql.tools.mapping.typeclass

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import jakarta.inject.Singleton

class TypeClassDerivationSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeClassDerivationSpec"

    static String schema(String union, String first, String second) {
        """
schema {
  query: Query
}

type Query {
  results: [$union!]!
}

union $union = $first | $second

type $first {
  title: String!
}

type $second {
  title: String!
}
"""
    }

    static String query(String first, String second) {
        "{ results { __typename ... on $first { title } ... on $second { title } } }"
    }

    void "a union whose members follow the convention needs no dictionary entry"() {
        given:
        startContext(schema('ConventionResult', 'Alpha', 'Beta'), SPEC_NAME,
                ['spec.scenario': 'convention', 'micronaut.graphql.tools.type-class-convention': '{Type}Graph'])

        when:
        def result = executeQuery(query('Alpha', 'Beta'))

        then:
        result.errors.isEmpty()
        result.data.results*.__typename == ['Alpha', 'Beta']
        result.data.results*.title == ['alpha', 'beta']
    }

    void "a union whose members declare their name needs no dictionary entry and no convention"() {
        given:
        startContext(schema('NamedResult', 'Gamma', 'Delta'), SPEC_NAME, ['spec.scenario': 'named'])

        when:
        def result = executeQuery(query('Gamma', 'Delta'))

        then:
        result.errors.isEmpty()
        result.data.results*.__typename == ['Gamma', 'Delta']
    }

    void "two classes that match one member fail the startup and name both"() {
        given:
        startContext(schema('AmbiguousResult', 'Epsilon', 'Zeta'), SPEC_NAME,
                ['spec.scenario': 'ambiguous', 'micronaut.graphql.tools.type-class-convention': '{Type}Graph'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.AMBIGUOUS_TYPE_CLASS
        e.cause.message.contains('Epsilon')
        e.cause.message.contains(EpsilonGraph.name)
        e.cause.message.contains(EpsilonModel.name)
    }

    void "an explicit dictionary entry overrides the convention"() {
        given:
        startContext(schema('OverrideResult', 'Eta', 'Theta'), SPEC_NAME,
                ['spec.scenario': 'override', 'micronaut.graphql.tools.type-class-convention': '{Type}Graph'])

        when:
        def result = executeQuery(query('Eta', 'Theta'))

        then:
        result.errors.isEmpty()
        result.data.results*.title == ['explicit', 'theta']
    }

    void "a convention without the placeholder fails the startup"() {
        given:
        startContext(schema('ConventionResult', 'Alpha', 'Beta'), SPEC_NAME,
                ['spec.scenario': 'convention', 'micronaut.graphql.tools.type-class-convention': 'Graph'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.code() == BindingCode.INVALID_TYPE_CLASS_CONVENTION
    }

    interface ConventionResult {
    }

    interface NamedResult {
    }

    interface AmbiguousResult {
    }

    interface OverrideResult {
    }

    @GraphQLType
    static class AlphaGraph implements ConventionResult {
        String title = 'alpha'
    }

    @GraphQLType
    static class BetaGraph implements ConventionResult {
        String title = 'beta'
    }

    @GraphQLType(name = 'Gamma')
    static class GammaModel implements NamedResult {
        String title = 'gamma'
    }

    @GraphQLType(name = 'Delta')
    static class DeltaModel implements NamedResult {
        String title = 'delta'
    }

    @GraphQLType
    static class EpsilonGraph implements AmbiguousResult {
        String title = 'epsilon'
    }

    @GraphQLType(name = 'Epsilon')
    static class EpsilonModel implements AmbiguousResult {
        String title = 'epsilon'
    }

    @GraphQLType(name = 'Zeta')
    static class ZetaModel implements AmbiguousResult {
        String title = 'zeta'
    }

    @GraphQLType
    static class EtaGraph implements OverrideResult {
        String title = 'eta'
    }

    @GraphQLType
    static class EtaExplicit implements OverrideResult {
        String title = 'explicit'
    }

    @GraphQLType
    static class ThetaGraph implements OverrideResult {
        String title = 'theta'
    }

    @Requires(property = 'spec.scenario', value = 'convention')
    @Singleton
    @GraphQLRootResolver
    static class ConventionQuery {
        List<ConventionResult> results() {
            [new AlphaGraph(), new BetaGraph()]
        }
    }

    @Requires(property = 'spec.scenario', value = 'named')
    @Singleton
    @GraphQLRootResolver
    static class NamedQuery {
        List<NamedResult> results() {
            [new GammaModel(), new DeltaModel()]
        }
    }

    @Requires(property = 'spec.scenario', value = 'ambiguous')
    @Singleton
    @GraphQLRootResolver
    static class AmbiguousQuery {
        List<AmbiguousResult> results() {
            [new EpsilonGraph(), new ZetaModel()]
        }
    }

    @Requires(property = 'spec.scenario', value = 'override')
    @Singleton
    @GraphQLRootResolver
    static class OverrideQuery {
        List<OverrideResult> results() {
            [new EtaExplicit(), new ThetaGraph()]
        }
    }

    @Requires(property = 'spec.scenario', value = 'override')
    @Factory
    static class OverrideDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer dictionary() {
            return { it.registerType('Eta', EtaExplicit) } as SchemaMappingDictionaryCustomizer
        }
    }

}
