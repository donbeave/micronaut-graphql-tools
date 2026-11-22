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
package io.micronaut.graphql.tools.micrometer

import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation

class BeansSpec extends AbstractMicrometerSpec {

    void "without a MeterRegistry bean the module adds no Instrumentation"() {
        given:
        start('MicrometerBeans', [:], false)

        expect:
        !context.containsBean(Instrumentation)
        !hasBean('MicrometerObserver')
        run('{ hello }').errors.isEmpty()
    }

    void "with a MeterRegistry bean, and OpenTelemetry not on the classpath, the library has exactly one Instrumentation bean"() {
        given:
        start('MicrometerBeans')

        expect:
        !classpathHas('io.opentelemetry.api.OpenTelemetry')
        context.getBeansOfType(Instrumentation).size() == 1
        installed(context.getBean(GraphQL)).count { it.getClass().simpleName == 'ObserverInstrumentation' } == 1
        hasBean('MicrometerObserver')
    }

    // graphql-java 21 chains whatever instrumentation it is given with its own, so the bean is looked for in the chain
    private static List<Instrumentation> installed(GraphQL graphQL) {
        def root = graphQL.instrumentation
        return root instanceof ChainedInstrumentation ? ((ChainedInstrumentation) root).instrumentations : [root]
    }

    private static boolean classpathHas(String name) {
        try {
            Class.forName(name)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }

}
