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
package io.micronaut.graphql.tools.observability

import graphql.ExecutionResult
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import reactor.core.publisher.Mono
import spock.lang.Specification

/** Starts a context with a MeterRegistry and an OpenTelemetry SDK that exports to memory, and runs operations through the library. */
abstract class AbstractObservabilitySpec extends Specification {

    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  broken: String
  book: Book
  loaded: String
}

type Book {
  title: String
}
"""

    ApplicationContext context
    SimpleMeterRegistry meters
    InMemorySpanExporter spans

    protected void start(String specName, Map config = [:], boolean metrics = true, boolean tracing = true) {
        meters = new SimpleMeterRegistry()
        spans = InMemorySpanExporter.create()
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
                .build()
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(SCHEMA))
        def builder = ApplicationContext.builder(
                ['spec.name'                                      : specName,
                 'graphql.graphql-ws.enabled'                     : false,
                 'graphql.graphiql.enabled'                       : false,
                 'micronaut.graphql.tools.execution.executor'     : 'none'] << config, 'test')
                .singletons(registry)
        if (metrics) {
            builder.singletons(meters)
        }
        if (tracing) {
            builder.singletons(openTelemetry)
        }
        context = builder.start()
    }

    protected ExecutionResult run(String query, String operationName = null) {
        Mono.from(context.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, operationName, [:]), null, null)).block()
    }

    protected List<SpanData> finished() {
        spans.finishedSpanItems
    }

    void cleanup() {
        context?.close()
    }

}
