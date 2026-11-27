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
 * The compile-time rules of the GraphQL client: the layout of the client directories, the parsing and assembly of an
 * operation document with the fragments it uses, the build of a client schema and the validation of the assembled document
 * against it. The compiler adapter (the visitor of {@code graphql-tools-processor}) and the code generator call it, so that
 * both see the same document.
 *
 * <p><b>Audience.</b> Internal; do not import it from application code.</p>
 */
@Internal
package io.micronaut.graphql.tools.binding.internal.client;

import io.micronaut.core.annotation.Internal;
