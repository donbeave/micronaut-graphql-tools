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
package io.micronaut.graphql.tools.opentelemetry.internal;

import graphql.ExecutionResult;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;

/**
 * Marks the span of a request {@code ERROR} when the execution failed or its result has an error.
 */
@Internal
final class RequestSpanStatus implements SpanStatusExtractor<OperationRequest, ExecutionResult> {

    @Override
    public void extract(SpanStatusBuilder status, OperationRequest request, @Nullable ExecutionResult result,
                        @Nullable Throwable error) {
        if (error != null) {
            status.setStatus(StatusCode.ERROR, error.getMessage() == null ? error.getClass().getName() : error.getMessage());
        } else if (result != null && !result.getErrors().isEmpty()) {
            int errors = result.getErrors().size();
            status.setStatus(StatusCode.ERROR, errors + (errors == 1 ? " error" : " errors"));
        }
    }

}
