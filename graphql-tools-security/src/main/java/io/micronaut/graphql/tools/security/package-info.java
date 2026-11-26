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
 * The names and the hook of the security module: the prefix of its configuration, the context key that holds the
 * {@code Authentication} of an execution, the error codes of a rejected field and the {@code GraphQLRootFieldsHook} that an
 * application implements to see the root fields of an operation. The module integrates Micronaut Security: the principal in
 * the context of an execution, {@code @Secured} on resolver methods, and the hook.
 *
 * <p><b>Audience.</b> Public API. An application reads the constants and implements the hook; it never calls into the
 * implementation.</p>
 */
package io.micronaut.graphql.tools.security;
