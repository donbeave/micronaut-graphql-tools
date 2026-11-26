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
package io.micronaut.graphql.tools.execution

import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.internal.resolver.FlowAdapter
import io.micronaut.graphql.tools.internal.wiring.FieldFetchers
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

/**
 * Kotlin Flow subscriptions without Kotlin: the classpath of this module's tests has no kotlinx-coroutines-reactive, as a
 * Java-only application has none. The Kotlin suite runs the real Flow subscriptions.
 */
class FlowSupportSpec extends Specification {

    void "the Flow adapter bean does not exist without kotlinx-coroutines-reactive"() {
        given:
        def context = ApplicationContext.run(['spec.name': 'FlowSupportSpec', 'micronaut.graphql.tools.eager-init': false])

        expect:
        context.findBean(FlowAdapter).empty
        context.getBeanDefinitions(FlowAdapter).empty

        cleanup:
        context.close()
    }

    void "binding a Flow method without kotlinx-coroutines-reactive fails the startup with its fix"() {
        given:
        def context = ApplicationContext.run(['spec.name': 'FlowSupportSpec', 'micronaut.graphql.tools.eager-init': false])
        def method = Stub(ExecutableMethod) {
            getDeclaringType() >> String
            getMethodName() >> 'ticks'
            getArgumentTypes() >> ([] as Class[])
        }

        when:
        FieldFetchers.flowAdapter(context, method, 'Subscription.ticks')

        then:
        SchemaBindingException e = thrown()
        e.getCode() == BindingCode.FLOW_WITHOUT_REACTIVE_COROUTINES
        e.message.startsWith('[flow-without-reactive-coroutines]')
        e.message.contains('Subscription.ticks')
        e.message.contains('org.jetbrains.kotlinx:kotlinx-coroutines-reactive')

        cleanup:
        context.close()
    }
}
