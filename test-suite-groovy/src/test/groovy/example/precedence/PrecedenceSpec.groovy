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
package example.precedence

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import spock.lang.Specification

// The Groovy suite runs on the JVM: the precedence rule for a field that a property and a type resolver method can
// both bind.
class PrecedenceSpec extends Specification {

    static final String PRECEDENCE = 'micronaut.graphql.tools.precedence'

    private static Object run(Map<String, Object> properties, String environment, String query) {
        ApplicationContext context = ApplicationContext.run(properties, environment)
        try {
            def result = GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build().execute(query)
            assert result.errors.isEmpty(): result.errors.toString()
            return result.getData()
        } finally {
            context.close()
        }
    }

    private static Throwable failure(Map<String, Object> properties, String environment) {
        try {
            run(properties, environment, '{ __typename }')
            return null
        } catch (Exception e) {
            Throwable cause = e
            while (!(cause instanceof SchemaBindingException) && cause.cause != null && !cause.cause.is(cause)) {
                cause = cause.cause
            }
            return cause
        }
    }

    void 'a resolver that declares its coordinate binds the field next to a property'() {
        expect:
        run([:], PrecedenceFixtures.RELAY, '{ gizmo { id ... on Sprocket { name } } }') ==
                [gizmo: [id: 'global', name: 'cog']]
    }

    void 'an ambiguous field fails the startup by default and strict false does not change it'() {
        expect:
        failure(properties, PrecedenceFixtures.AMBIGUOUS) instanceof SchemaBindingException
        failure(properties, PrecedenceFixtures.AMBIGUOUS).message.startsWith('[precedence-ambiguity]')
        // a failure about one field names its schema coordinate and is no aggregate
        failure(properties, PrecedenceFixtures.AMBIGUOUS).getCode() == BindingCode.PRECEDENCE_AMBIGUITY
        failure(properties, PrecedenceFixtures.AMBIGUOUS).coordinate == 'Gadget.label'
        failure(properties, PrecedenceFixtures.AMBIGUOUS).mappingContext.coordinate == 'Gadget.label'
        failure(properties, PrecedenceFixtures.AMBIGUOUS).failures.empty
        failure(properties, PrecedenceFixtures.AMBIGUOUS).codes.empty

        where:
        properties << [[:], ['micronaut.graphql.tools.strict': false]]
    }

    void 'a broken binding fails the startup with a ConfigurationException in the cause chain'() {
        when:
        run([:], PrecedenceFixtures.AMBIGUOUS, '{ __typename }')

        then:
        Throwable thrown = thrown(Exception)
        ConfigurationException e = causes(thrown).find { it instanceof ConfigurationException } as ConfigurationException
        e != null
        e instanceof SchemaBindingException
        e.code == BindingCode.PRECEDENCE_AMBIGUITY
        e.message.startsWith('[precedence-ambiguity]')
    }

    private static List<Throwable> causes(Throwable e) {
        List<Throwable> chain = []
        for (Throwable cause = e; cause != null && !chain.contains(cause); cause = cause.cause) {
            chain << cause
        }
        return chain
    }

    void 'the lenient settings decide the field'() {
        expect:
        run([(PRECEDENCE): 'resolver-first'], PrecedenceFixtures.AMBIGUOUS, '{ gadget { label } }') ==
                [gadget: [label: 'resolved pen']]
        run([(PRECEDENCE): 'property-first'], PrecedenceFixtures.AMBIGUOUS, '{ gadget { label } }') ==
                [gadget: [label: 'own']]
    }

    void 'the removed value strict fails the startup'() {
        expect:
        failure([(PRECEDENCE): 'strict'], PrecedenceFixtures.RELAY) instanceof SchemaBindingException
        failure([(PRECEDENCE): 'strict'], PrecedenceFixtures.RELAY).message.startsWith('[removed-configuration-key]')
    }

}
