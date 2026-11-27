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
 * The Gradle plugin of the GraphQL code generator: the plugin ({@link io.micronaut.graphql.tools.gradle.GraphQLToolsPlugin}), the
 * {@code graphqlTools} extension with its named code generation specs, the cacheable generation task and the worker action that runs
 * the generator in an isolated class loader. The generator is never on the build-script classpath or on the application's classpath.
 *
 * <p><b>Audience.</b> Public. A build script applies the plugin by its id, {@code io.micronaut.graphql.tools}, and configures the
 * extension; the task and the extension types are the API of that configuration.</p>
 */
package io.micronaut.graphql.tools.gradle;
