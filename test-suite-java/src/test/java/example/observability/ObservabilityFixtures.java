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

import graphql.schema.idl.SchemaParser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.inject.Singleton;

@Factory
public class ObservabilityFixtures {

    // The environment that turns the fixtures on: a meter registry, a span processor that exports to memory, and the observed fields
    public static final String OBSERVED = "observability";

    // tag::beans[]
    @Bean
    @Singleton
    @Requires(env = OBSERVED)
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @Singleton
    @Requires(env = OBSERVED)
    InMemorySpanExporter spanExporter() {
        return InMemorySpanExporter.create();
    }

    // the OpenTelemetry bean is the one of micronaut-tracing: it exports to the span processor beans
    @Bean
    @Singleton
    @Requires(env = OBSERVED)
    SpanProcessor spanProcessor(InMemorySpanExporter exporter) {
        return SimpleSpanProcessor.create(exporter);
    }
    // end::beans[]

    @Bean
    @Singleton
    @Requires(env = OBSERVED)
    GraphQLTypeDefinitionRegistryCustomizer observedFields() {
        return registry -> registry.merge(new SchemaParser().parse(
                "extend type Query {\n"
                        + "  observedHello: String!\n"
                        + "  observedBook: ObservedBook!\n"
                        + "  observedTraced: String!\n"
                        + "  observedTagged(value: String!): String!\n"
                        + "  observedTimed: String!\n"
                        + "}\n"
                        + "type ObservedBook {\n"
                        + "  title: String!\n"
                        + "}\n"));
    }

}
