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
 * The nodes of the IR that only client mode fills: the operations of a client with their assembled documents, the variables and
 * the selection sets, and the named fragments that the operations use. The result models that an operation selects are ordinary
 * {@link io.micronaut.graphql.tools.codegen.ir.ModelType} and {@link io.micronaut.graphql.tools.codegen.ir.AbstractType} nodes of
 * the IR, derived from the selection sets, so an emitter needs no new capability for them.
 *
 * <p><b>Audience.</b> Public. It is meant for the emitters and the build plugins of the generator.</p>
 */
package io.micronaut.graphql.tools.codegen.ir.client;
