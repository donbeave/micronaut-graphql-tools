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
import io.micronaut.graphql.tools.internal.execution.SuspendInvoker
import io.micronaut.graphql.tools.internal.wiring.FieldFetchers
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

/**
 * Kotlin suspend resolvers without Kotlin: the classpath of this module's tests has neither the Kotlin standard library nor
 * kotlinx-coroutines-core, as a Java-only application has none. The Kotlin suite runs the real suspend resolvers.
 */
class SuspendSupportSpec extends Specification {

    void "the suspend support bean does not exist without the Kotlin classes"() {
        given:
        def context = ApplicationContext.run(['spec.name': 'SuspendSupportSpec', 'micronaut.graphql.tools.eager-init': false])

        expect:
        context.findBean(SuspendInvoker).empty
        context.getBeanDefinitions(SuspendInvoker).empty

        cleanup:
        context.close()
    }

    void "binding a suspend method without kotlinx-coroutines-core fails the startup with its fix"() {
        given:
        def context = ApplicationContext.run(['spec.name': 'SuspendSupportSpec', 'micronaut.graphql.tools.eager-init': false])
        def method = Stub(ExecutableMethod) {
            isSuspend() >> true
            getDeclaringType() >> String
            getMethodName() >> 'find'
            getArgumentTypes() >> ([String] as Class[])
        }

        when:
        FieldFetchers.suspendInvoker(context, method, 'Query.find')

        then:
        SchemaBindingException e = thrown()
        e.getCode() == BindingCode.SUSPEND_WITHOUT_COROUTINES
        e.message.startsWith('[suspend-without-coroutines]')
        e.message.contains('Query.find')
        e.message.contains('org.jetbrains.kotlinx:kotlinx-coroutines-core')

        cleanup:
        context.close()
    }

    void "a method that is not suspend needs no invoker"() {
        given:
        def context = ApplicationContext.run(['spec.name': 'SuspendSupportSpec', 'micronaut.graphql.tools.eager-init': false])
        def method = Stub(ExecutableMethod) {
            isSuspend() >> false
        }

        expect:
        FieldFetchers.suspendInvoker(context, method, 'Query.find') == null

        cleanup:
        context.close()
    }
}
