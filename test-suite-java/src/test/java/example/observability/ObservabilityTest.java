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
package example.observability;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.ApplicationContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the request timer of the Micrometer module and the spans of the OpenTelemetry module.
class ObservabilityTest {

    private static ExecutionResult execute(ApplicationContext context, String query, String operationName) {
        return context.getBean(GraphQL.class)
                .execute(ExecutionInput.newExecutionInput().query(query).operationName(operationName).build());
    }

    // tag::metrics[]
    @Test
    void aRequestHasATimerTaggedByOperationNameAndOutcome() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), ObservabilityFixtures.OBSERVED)) {
            ExecutionResult result = execute(context, "query Hello { observedHello }", "Hello");
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());

            Timer timer = context.getBean(MeterRegistry.class).find("graphql.request")
                    .tags("operation", "Hello", "outcome", "success").timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
        }
    }
    // end::metrics[]

    // tag::tracing[]
    @Test
    void selectedFieldsGetSpansAndPropertyReadsDoNot() {
        Map<String, Object> properties = Map.of("micronaut.graphql.tools.tracing.fields",
                List.of("Query.observedHello", "ObservedBook.*"));
        try (ApplicationContext context = ApplicationContext.run(properties, ObservabilityFixtures.OBSERVED)) {
            ExecutionResult result = execute(context, "query Both { observedHello observedBook { title } }", "Both");
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());

            List<SpanData> spans = context.getBean(InMemorySpanExporter.class).getFinishedSpanItems();
            assertEquals(List.of("Query.observedHello", "query"),
                    spans.stream().map(SpanData::getName).sorted().collect(Collectors.toList()));
        }
    }
    // end::tracing[]

    // tag::span-name[]
    @Test
    void theRequestSpanIsNamedAfterTheOperationTypeUnlessTheKeyAddsTheName() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), ObservabilityFixtures.OBSERVED)) {
            execute(context, "query Hello { observedHello }", "Hello");
            assertEquals(List.of("query"), names(context));
        }
        Map<String, Object> properties = Map.of("micronaut.graphql.tools.tracing.add-operation-name-to-span-name", true);
        try (ApplicationContext context = ApplicationContext.run(properties, ObservabilityFixtures.OBSERVED)) {
            execute(context, "query Hello { observedHello }", "Hello");
            assertEquals(List.of("query Hello"), names(context));
        }
    }
    // end::span-name[]

    // tag::new-span[]
    @Test
    void aNewSpanResolverIsAChildOfTheRequestSpan() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), ObservabilityFixtures.OBSERVED)) {
            ExecutionResult result = execute(context,
                    "query Work { observedTraced observedTagged(value: \"x\") }", "Work");
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());

            List<SpanData> spans = context.getBean(InMemorySpanExporter.class).getFinishedSpanItems();
            SpanData request = spans.stream().filter(span -> span.getName().equals("query")).findFirst().orElseThrow();
            SpanData work = spans.stream().filter(span -> span.getName().endsWith("#observed.work")).findFirst().orElseThrow();
            SpanData tagged = spans.stream().filter(span -> span.getName().endsWith("#observed.tagged")).findFirst().orElseThrow();
            assertEquals(request.getSpanId(), work.getParentSpanId());
            assertEquals("x", tagged.getAttributes().get(AttributeKey.stringKey("observed.value")));
        }
    }
    // end::new-span[]

    // tag::timed-resolver[]
    @Test
    void aTimedResolverHasATimer() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), ObservabilityFixtures.OBSERVED)) {
            ExecutionResult result = execute(context, "query Work { observedTimed }", "Work");
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());

            Timer timer = context.getBean(MeterRegistry.class).find("observed.timed").timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
        }
    }
    // end::timed-resolver[]

    private static List<String> names(ApplicationContext context) {
        return context.getBean(InMemorySpanExporter.class).getFinishedSpanItems().stream().map(SpanData::getName)
                .collect(Collectors.toList());
    }

    @Test
    void theCardinalityOfTheOperationTagIsCapped() {
        Map<String, Object> properties = Map.of("micronaut.metrics.binders.graphql.max-operations", 2);
        try (ApplicationContext context = ApplicationContext.run(properties, ObservabilityFixtures.OBSERVED)) {
            for (int i = 0; i < 10; i++) {
                execute(context, "query Op" + i + " { observedHello }", null);
            }
            long operations = context.getBean(MeterRegistry.class).find("graphql.request").timers().stream()
                    .map(timer -> timer.getId().getTag("operation")).distinct().count();
            assertEquals(3, operations);
        }
    }

    @Test
    void theLibraryHasOneInstrumentationBeanWhicheverModulesAreOn() {
        assertEquals(1, instrumentations(Map.of()));
        assertEquals(1, instrumentations(Map.of("micronaut.graphql.tools.tracing.enabled", false)));
        assertEquals(1, instrumentations(Map.of("micronaut.metrics.binders.graphql.enabled", false)));
        assertEquals(0, instrumentations(Map.of("micronaut.graphql.tools.tracing.enabled", false,
                "micronaut.metrics.binders.graphql.enabled", false)));
    }

    private static int instrumentations(Map<String, Object> properties) {
        try (ApplicationContext context = ApplicationContext.run(properties, ObservabilityFixtures.OBSERVED)) {
            return context.getBeansOfType(Instrumentation.class).size();
        }
    }

}
