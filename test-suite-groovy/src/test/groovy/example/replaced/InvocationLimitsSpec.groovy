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
package example.replaced

import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import spock.lang.Specification

class InvocationLimitsSpec extends Specification {

    static final Map<String, Object> PORT = ['micronaut.server.port': -1]

    def 'an invocation that is only secondary fails the startup with the way out'() {
        when:
        ApplicationContext.run(PORT, InvocationLimitsFactory.SECONDARY)

        then:
        def e = thrown(Throwable)
        def message = configuration(e).message
        message.contains('@Secondary')
        message.contains('Fix: remove @Secondary')
    }

    def 'a schema template over every invocation fails the startup with the way out'() {
        when:
        ApplicationContext.run(PORT, InvocationLimitsFactory.EACH_INVOCATION)

        then:
        def e = thrown(Throwable)
        def message = configuration(e).message
        message.contains('@EachBean(GraphQLInvocation.class)')
        message.contains("target the application's own bean type")
    }

    def 'a factory default replaced by qualifier does not disable the library invocation'() {
        given:
        ApplicationContext context = ApplicationContext.run(PORT + ['micronaut.graphql.tools.eager-init': false], InvocationLimitsFactory.QUALIFIED_DEFAULT)

        expect:
        def types = context.getBeanDefinitions(GraphQLInvocation).collect { it.beanType.simpleName } as Set
        types.contains('LibraryGraphQLInvocation')
        !types.contains('DefaultGraphQLInvocation')

        cleanup:
        context?.close()
    }

    private static ConfigurationException configuration(Throwable failure) {
        Throwable current = failure
        while (current != null) {
            if (current instanceof ConfigurationException) {
                return current
            }
            current = current.cause
        }
        throw new AssertionError('no ConfigurationException', failure)
    }

}
