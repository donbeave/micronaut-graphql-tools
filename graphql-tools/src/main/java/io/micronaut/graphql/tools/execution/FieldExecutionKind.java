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

/** How the library executes a field, decided once at startup. */
public enum FieldExecutionKind {
    /** A property or an introspection read: never wrapped by the library. */
    TRIVIAL,
    /** A method with a plain return: runs on the thread that resolves its parent. */
    SYNCHRONOUS,
    /** A method that returns a {@code CompletionStage} or a publisher: passes through the async boundary. */
    ASYNC,
    /** A root field of the subscription type, which streams. */
    STREAM
}
