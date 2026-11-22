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

import graphql.ExecutionResult
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.ApplicationContext
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/** Starts a context with a MeterRegistry and the OpenTelemetry SDK of micronaut-tracing, whose span processor exports to memory, and runs operations through the library. */
abstract class AbstractOpenTelemetrySpec extends Specification {

    static final String SCHEMA = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  hello: String
  broken: String
  book: Book
  loaded: String
  traced: String
  tracedOnExecutor: String
  tagged(value: String): String
  later: Book
}

type Subscription {
  ticks: String
}

type Book {
  title: String
}
"""

    ApplicationContext context
    SimpleMeterRegistry meters
    InMemorySpanExporter spans
    AtomicInteger startedSpans = new AtomicInteger()

    protected void start(String specName, Map config = [:], boolean metrics = false, boolean tracing = true) {
        meters = new SimpleMeterRegistry()
        spans = InMemorySpanExporter.create()
        startedSpans.set(0)
        def exporter = SimpleSpanProcessor.create(spans)
        SpanProcessor processor = new SpanProcessor() {
            void onStart(Context parentContext, ReadWriteSpan span) { startedSpans.incrementAndGet() }
            boolean isStartRequired() { true }
            void onEnd(ReadableSpan span) { exporter.onEnd(span) }
            boolean isEndRequired() { true }
        }
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(SCHEMA))
        def builder = ApplicationContext.builder(
                ['spec.name'                                      : specName,
                 'graphql.graphql-ws.enabled'                     : false,
                 'graphql.graphiql.enabled'                       : false,
                 'micronaut.graphql.tools.execution.executor'     : 'none',
                 'micronaut.metrics.enabled'                      : metrics,
                 'micronaut.otel.enabled'                         : tracing] << config, 'test')
                .singletons(registry)
        if (metrics) {
            builder.singletons(meters)
        }
        if (tracing) {
            builder.singletons(processor)
        }
        context = builder.start()
    }

    protected ExecutionResult run(String query, String operationName = null) {
        Mono.from(context.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, operationName, [:]), null, null)).block()
    }

    protected boolean hasBean(String simpleName) {
        context.containsBean(Class.forName('io.micronaut.graphql.tools.opentelemetry.internal.' + simpleName))
    }

    protected List<SpanData> finished() {
        spans.finishedSpanItems
    }

    void cleanup() {
        context?.close()
    }

}
