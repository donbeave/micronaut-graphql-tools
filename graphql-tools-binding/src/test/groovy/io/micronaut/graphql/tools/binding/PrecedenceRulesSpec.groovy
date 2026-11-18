package io.micronaut.graphql.tools.binding

import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules
import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules.Outcome
import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules.Setting
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules
import spock.lang.Specification

import static io.micronaut.graphql.tools.binding.FakeMethods.coordinate
import static io.micronaut.graphql.tools.binding.FakeMethods.method

class PrecedenceRulesSpec extends Specification {

    void "one rule decides a field that a member and a type resolver method can both bind (#setting, intent #intent)"() {
        expect:
        PrecedenceRules.decide(setting, intent) == outcome

        where:
        setting                | intent | outcome
        Setting.FAIL           | false  | Outcome.FAIL
        Setting.RESOLVER_FIRST | false  | Outcome.RESOLVER
        Setting.PROPERTY_FIRST | false  | Outcome.MEMBER
        Setting.FAIL           | true   | Outcome.RESOLVER
        Setting.RESOLVER_FIRST | true   | Outcome.RESOLVER
        Setting.PROPERTY_FIRST | true   | Outcome.RESOLVER
    }

    void "the settings are read by their token, and the old strict is not one"() {
        expect:
        Setting.values()*.token() == ['fail', 'resolver-first', 'property-first']
        Setting.fromToken(' Resolver-First ') == Setting.RESOLVER_FIRST
        Setting.fromToken('strict') == null
        Setting.fromToken(null) == null
    }

    void "a method declares its coordinate when the class lists its signature"() {
        given:
        def declared = ['a.R#id()': 'Node.id']

        expect:
        ResolverClassRules.declaresCoordinate(method('id', coordinate('Node.id'), [], null, 'a.R#id()'), declared)
        !ResolverClassRules.declaresCoordinate(method('name', [:], [], null, 'a.R#name()'), declared)
        !ResolverClassRules.declaresCoordinate(method('id'), null)
    }

}
