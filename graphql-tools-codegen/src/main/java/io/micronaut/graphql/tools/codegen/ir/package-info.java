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
 * The intermediate representation (IR) of the code generator: a tree of immutable records that is language neutral and is a
 * pure function of the schema files and the options. The rules that name, map and group the schema produce it and the emitters
 * read nothing else, so a golden test can print it with {@link io.micronaut.graphql.tools.codegen.ir.IrSnapshot}.
 *
 * <p>A node carries the SDL name and the generated name side by side ({@link io.micronaut.graphql.tools.codegen.ir.shared.Names}),
 * the nullability of every type reference, its documentation (description and deprecation) and its origin: the schema file
 * relative to the schema root, the line and the column. The origin is for diagnostics and snapshots and is never written into
 * generated code. No node holds a graphql-java object, and every list is in schema order.</p>
 *
 * <p><b>Audience.</b> Public. It is meant for the emitters and the build plugins of the generator.</p>
 */
package io.micronaut.graphql.tools.codegen.ir;
