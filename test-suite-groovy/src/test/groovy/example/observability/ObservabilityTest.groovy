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
package example.observability

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import spock.lang.Specification

// The Groovy suite runs on the JVM: the request timer and the spans of the observability module.
class ObservabilityTest extends Specification {

    private static ExecutionResult execute(ApplicationContext context, String query, String operationName) {
        context.getBean(GraphQL).execute(ExecutionInput.newExecutionInput().query(query).operationName(operationName).build())
    }

    // tag::metrics[]
    void 'a request has a timer tagged by operation name and outcome'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], ObservabilityFixtures.OBSERVED)

        when:
        def result = execute(context, 'query Hello { observedHello }', 'Hello')
        def timer = context.getBean(MeterRegistry).find('graphql.request').tags('operation', 'Hello', 'outcome', 'success').timer()

        then:
        result.errors.isEmpty()
        timer != null
        timer.count() == 1

        cleanup:
        context.close()
    }
    // end::metrics[]

    // tag::tracing[]
    void 'selected fields get spans and property reads do not'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ['micronaut.graphql.tools.observability.tracing.fields': ['Query.observedHello', 'ObservedBook.*']],
                ObservabilityFixtures.OBSERVED)

        when:
        def result = execute(context, 'query Both { observedHello observedBook { title } }', 'Both')
        def spans = context.getBean(InMemorySpanExporter).finishedSpanItems

        then:
        result.errors.isEmpty()
        spans*.name.sort() == ['Query.observedHello', 'query Both']

        cleanup:
        context.close()
    }
    // end::tracing[]

    void 'the cardinality of the operation tag is capped'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ['micronaut.graphql.tools.observability.metrics.max-operations': 2], ObservabilityFixtures.OBSERVED)

        when:
        (0..<10).each { execute(context, "query Op$it { observedHello }", null) }
        def operations = context.getBean(MeterRegistry).find('graphql.request').timers()*.id*.getTag('operation').unique()

        then:
        operations.size() == 3

        cleanup:
        context.close()
    }

}
