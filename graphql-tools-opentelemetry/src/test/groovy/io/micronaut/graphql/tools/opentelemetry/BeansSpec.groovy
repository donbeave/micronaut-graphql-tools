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
package io.micronaut.graphql.tools.opentelemetry

import graphql.GraphQL
import graphql.execution.instrumentation.Instrumentation
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.micrometer.GraphQLMetrics
import jakarta.inject.Singleton
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.ChainedInstrumentation

/** Which beans the module creates, and how its instrumentation sits in the chain the library builds. */
class BeansSpec extends AbstractOpenTelemetrySpec {

    void "with neither a MeterRegistry nor an OpenTelemetry bean the module adds no Instrumentation"() {
        given:
        start('OpenTelemetryBeans', [:], false, false)

        expect:
        !context.containsBean(Instrumentation)
        run('{ hello }').errors.isEmpty()
    }

    void "one instrumentation serves metrics and tracing together, installed as it is"() {
        given:
        start('OpenTelemetryBeans', [:], true, true)

        expect:
        context.getBeansOfType(Instrumentation).size() == 1
        installed(context.getBean(GraphQL)).count { it.getClass().simpleName == 'ObserverInstrumentation' } == 1
    }

    void "metrics alone are enough, and add exactly one Instrumentation"() {
        given:
        start('OpenTelemetryBeans', [:], true, false)

        when:
        run('query Hello { hello }')

        then:
        context.getBeansOfType(Instrumentation).size() == 1
        !hasBean('RequestSpanObserver')
        meters.find(GraphQLMetrics.REQUEST_TIMER).timer().count() == 1
        finished().isEmpty()
    }

    void "tracing alone is enough, and adds exactly one Instrumentation"() {
        given:
        start('OpenTelemetryBeans', [:], false, true)

        when:
        run('query Hello { hello }')

        then:
        context.getBeansOfType(Instrumentation).size() == 1
        !context.containsBean(Class.forName('io.micronaut.graphql.tools.micrometer.internal.MicrometerObserver'))
        finished().size() == 1
    }

    void "the module's instrumentation and the application's observe one execution, in order"() {
        given:
        Recording.CALLS.clear()
        start('OpenTelemetryBeans.chained', [:], true, true)

        when:
        run('query Hello { hello }')

        then:
        context.getBean(GraphQL).instrumentation instanceof ChainedInstrumentation
        Recording.CALLS == ['first', 'second']
        meters.find(GraphQLMetrics.REQUEST_TIMER).timer().count() == 1
        finished().size() == 1
    }

    // graphql-java 21 chains whatever instrumentation it is given with its own, so the bean is looked for in the chain
    private static List<Instrumentation> installed(GraphQL graphQL) {
        def root = graphQL.instrumentation
        return root instanceof ChainedInstrumentation ? ((ChainedInstrumentation) root).instrumentations : [root]
    }

    static class Recording extends SimplePerformantInstrumentation {
        static final List<String> CALLS = Collections.synchronizedList([])
        final String name

        Recording(String name) {
            this.name = name
        }

        @Override
        graphql.execution.instrumentation.InstrumentationState createState(
                graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters parameters) {
            CALLS << name
            return null
        }
    }

    @Requires(property = 'spec.name', value = 'OpenTelemetryBeans.chained')
    @Singleton
    @Order(1)
    static class First extends Recording {
        First() { super('first') }
    }

    @Requires(property = 'spec.name', value = 'OpenTelemetryBeans.chained')
    @Singleton
    @Order(2)
    static class Second extends Recording {
        Second() { super('second') }
    }

}
