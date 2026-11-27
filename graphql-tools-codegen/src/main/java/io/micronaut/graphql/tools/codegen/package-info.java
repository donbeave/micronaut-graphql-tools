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
 * The GraphQL code generator library: the entry point ({@link io.micronaut.graphql.tools.codegen.GraphQLCodeGenerator}), the
 * immutable options model ({@link io.micronaut.graphql.tools.codegen.Options}) with its string form, and the diagnostics that the
 * generator and the options report. The generator is a plain Java library: it has no Gradle, Maven or Micronaut runtime types, and
 * the build plugins map their configuration onto {@code Options} one to one.
 *
 * <p><b>Audience.</b> Public. It is meant for build plugins and for code that runs a build step; an application never has it on
 * its class path.</p>
 */
package io.micronaut.graphql.tools.codegen;
