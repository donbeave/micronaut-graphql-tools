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
 * The pieces that the nodes of the IR are built from: the names of a generated type ({@link io.micronaut.graphql.tools.codegen.ir.shared.Names}),
 * its documentation, its applied directives, the references to a type with their nullability
 * ({@link io.micronaut.graphql.tools.codegen.ir.shared.TypeRef}), and the coordinate, operation and return shape of a resolver
 * contract method.
 *
 * <p><b>Audience.</b> Public. It is meant for the emitters and the build plugins of the generator.</p>
 */
package io.micronaut.graphql.tools.codegen.ir.shared;
