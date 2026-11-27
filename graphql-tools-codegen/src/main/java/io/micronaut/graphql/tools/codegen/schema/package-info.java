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
 * The loader and the rules of the code generator. The loader reads schema files with the graphql-java parser only (the one package
 * that touches graphql-java) and merges the definitions and the type extensions in a defined order; the rules lower the merged
 * definitions and the options into the {@link io.micronaut.graphql.tools.codegen.ir.Ir} and report diagnostics. The entry points
 * are {@link io.micronaut.graphql.tools.codegen.schema.IrBuilder} and its {@link io.micronaut.graphql.tools.codegen.schema.IrResult};
 * the rest of the package is package-private.
 *
 * <p><b>Audience.</b> Public. It is meant for build plugins and for code that runs a build step; an application never has it on
 * its class path.</p>
 */
package io.micronaut.graphql.tools.codegen.schema;
