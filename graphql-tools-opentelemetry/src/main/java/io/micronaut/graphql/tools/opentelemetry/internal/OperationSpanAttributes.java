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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/**
 * The OpenTelemetry GraphQL semantic-convention attributes of a request span, set when it ends, when the operation is known.
 */
@Internal
final class OperationSpanAttributes implements AttributesExtractor<OperationRequest, ExecutionResult> {

    private static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("graphql.operation.name");
    private static final AttributeKey<String> OPERATION_TYPE = AttributeKey.stringKey("graphql.operation.type");

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, OperationRequest request) {
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, OperationRequest request, @Nullable ExecutionResult result,
                      @Nullable Throwable error) {
        String type = request.type();
        if (type != null) {
            attributes.put(OPERATION_TYPE, type);
        }
        String name = request.name();
        if (name != null) {
            attributes.put(OPERATION_NAME, name);
        }
    }

}
