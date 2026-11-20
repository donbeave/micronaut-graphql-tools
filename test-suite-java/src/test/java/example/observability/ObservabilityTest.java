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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.ApplicationContext;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the request timer and the spans of the observability module.
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
        Map<String, Object> properties = Map.of("micronaut.graphql.tools.observability.tracing.fields",
                List.of("Query.observedHello", "ObservedBook.*"));
        try (ApplicationContext context = ApplicationContext.run(properties, ObservabilityFixtures.OBSERVED)) {
            ExecutionResult result = execute(context, "query Both { observedHello observedBook { title } }", "Both");
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());

            List<SpanData> spans = context.getBean(InMemorySpanExporter.class).getFinishedSpanItems();
            assertEquals(List.of("Query.observedHello", "query Both"),
                    spans.stream().map(SpanData::getName).sorted().collect(Collectors.toList()));
        }
    }
    // end::tracing[]

    @Test
    void theCardinalityOfTheOperationTagIsCapped() {
        Map<String, Object> properties = Map.of("micronaut.graphql.tools.observability.metrics.max-operations", 2);
        try (ApplicationContext context = ApplicationContext.run(properties, ObservabilityFixtures.OBSERVED)) {
            for (int i = 0; i < 10; i++) {
                execute(context, "query Op" + i + " { observedHello }", null);
            }
            long operations = context.getBean(MeterRegistry.class).find("graphql.request").timers().stream()
                    .map(timer -> timer.getId().getTag("operation")).distinct().count();
            assertEquals(3, operations);
        }
    }

}
