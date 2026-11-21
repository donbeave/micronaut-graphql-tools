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
package io.micronaut.graphql.tools.observability.internal;

import graphql.ExecutionResult;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import jakarta.inject.Singleton;

/**
 * Creates the {@link Instrumenter} of the request span from the application's {@link OpenTelemetry} bean, the way the HTTP
 * integration of Micronaut Tracing creates its instrumenters.
 */
@Internal
@Factory
@Requires(classes = {OpenTelemetry.class, Instrumenter.class})
@Requires(beans = OpenTelemetry.class)
@Requires(property = "micronaut.otel.enabled", notEquals = StringUtils.FALSE)
@Requires(property = GraphQLTracingConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
final class RequestSpanInstrumenterFactory {

    @Bean
    @Singleton
    Instrumenter<OperationRequest, ExecutionResult> requestInstrumenter(OpenTelemetry openTelemetry) {
        return Instrumenter.<OperationRequest, ExecutionResult>builder(openTelemetry, RequestSpanObserver.INSTRUMENTATION_NAME,
                        request -> RequestSpanObserver.FALLBACK_NAME)
                .addAttributesExtractor(new OperationSpanAttributes())
                .setSpanStatusExtractor(new RequestSpanStatus())
                .buildInstrumenter(SpanKindExtractor.alwaysInternal());
    }

}
