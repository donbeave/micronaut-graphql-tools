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
 * The Maven plugin of the GraphQL code generator: the {@code generate} goal ({@link io.micronaut.graphql.tools.maven.GenerateMojo})
 * maps a {@code <configuration>} one to one onto the options of the generator, writes the generated files, removes the files that an
 * earlier run generated and the current run does not, skips a run whose inputs did not change, and registers the generated source
 * directory of the language with the project.
 *
 * <p><b>Audience.</b> Public for the mojo, which Maven instantiates and whose parameters are the configuration of the plugin. Every
 * other type of the package is package-private. An application never has the plugin on its class path.</p>
 */
package io.micronaut.graphql.tools.maven;
