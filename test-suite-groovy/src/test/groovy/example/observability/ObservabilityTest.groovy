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
import io.opentelemetry.api.common.AttributeKey
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
                ['micronaut.graphql.tools.tracing.fields': ['Query.observedHello', 'ObservedBook.*']],
                ObservabilityFixtures.OBSERVED)

        when:
        def result = execute(context, 'query Both { observedHello observedBook { title } }', 'Both')
        def spans = context.getBean(InMemorySpanExporter).finishedSpanItems

        then:
        result.errors.isEmpty()
        spans*.name.sort() == ['Query.observedHello', 'query']

        cleanup:
        context.close()
    }
    // end::tracing[]

    // tag::span-name[]
    void 'the request span is named after the operation type unless the key adds the name'() {
        given:
        ApplicationContext plain = ApplicationContext.run([:], ObservabilityFixtures.OBSERVED)
        ApplicationContext named = ApplicationContext.run(
                ['micronaut.graphql.tools.tracing.add-operation-name-to-span-name': true], ObservabilityFixtures.OBSERVED)

        when:
        execute(plain, 'query Hello { observedHello }', 'Hello')
        execute(named, 'query Hello { observedHello }', 'Hello')

        then:
        plain.getBean(InMemorySpanExporter).finishedSpanItems*.name == ['query']
        named.getBean(InMemorySpanExporter).finishedSpanItems*.name == ['query Hello']

        cleanup:
        plain.close()
        named.close()
    }
    // end::span-name[]

    // tag::new-span[]
    void 'a new span resolver is a child of the request span'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], ObservabilityFixtures.OBSERVED)

        when:
        def result = execute(context, 'query Work { observedTraced observedTagged(value: "x") }', 'Work')
        def spans = context.getBean(InMemorySpanExporter).finishedSpanItems
        def request = spans.find { it.name == 'query' }
        def work = spans.find { it.name.endsWith('#observed.work') }
        def tagged = spans.find { it.name.endsWith('#observed.tagged') }

        then:
        result.errors.isEmpty()
        work.parentSpanId == request.spanId
        tagged.attributes.get(AttributeKey.stringKey('observed.value')) == 'x'

        cleanup:
        context.close()
    }
    // end::new-span[]

    // tag::timed-resolver[]
    void 'a timed resolver has a timer'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], ObservabilityFixtures.OBSERVED)

        when:
        def result = execute(context, 'query Work { observedTimed }', 'Work')
        def timer = context.getBean(MeterRegistry).find('observed.timed').timer()

        then:
        result.errors.isEmpty()
        timer != null
        timer.count() == 1

        cleanup:
        context.close()
    }
    // end::timed-resolver[]

    void 'the cardinality of the operation tag is capped'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ['micronaut.metrics.binders.graphql.max-operations': 2], ObservabilityFixtures.OBSERVED)

        when:
        (0..<10).each { execute(context, "query Op$it { observedHello }", null) }
        def operations = context.getBean(MeterRegistry).find('graphql.request').timers()*.id*.getTag('operation').unique()

        then:
        operations.size() == 3

        cleanup:
        context.close()
    }

}
