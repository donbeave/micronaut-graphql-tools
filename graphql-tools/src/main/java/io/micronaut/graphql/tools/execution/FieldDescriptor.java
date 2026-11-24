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
package io.micronaut.graphql.tools.execution;

import graphql.schema.FieldCoordinates;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * What a {@link GraphQLFieldFetcherDecorator} learns about a field at startup.
 *
 * @param coordinates        the coordinates of the field
 * @param kind               how the library executes the field
 * @param hasArguments       whether the schema declares arguments on the field
 * @param fieldExecutor      the executor that {@code @ExecuteOn} names when it differs from the executor of the operation, else null
 * @param annotationMetadata the annotation metadata of the method or property that serves the field
 * @param resolver           the method or property that serves the field, as the declaring class, a space and the signature (for messages)
 */
public record FieldDescriptor(@NonNull FieldCoordinates coordinates, @NonNull FieldExecutionKind kind, boolean hasArguments,
                              @Nullable Executor fieldExecutor, @NonNull AnnotationMetadata annotationMetadata,
                              @NonNull String resolver) {
}
