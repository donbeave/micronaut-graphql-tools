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
/**
 * The library's GraphQL invocation: the one executor hop of an operation, the per-execution state in the GraphQL context
 * and the rules for when an application's own invocation replaces it.
 *
 * <p><b>Audience.</b> Internal. Not part of the API; do not import it. Key types: LibraryGraphQLInvocation, ExecutionState,
 * ExecutionContextAvailability.</p>
 */
@Internal
package io.micronaut.graphql.tools.internal.execution;

import io.micronaut.core.annotation.Internal;
