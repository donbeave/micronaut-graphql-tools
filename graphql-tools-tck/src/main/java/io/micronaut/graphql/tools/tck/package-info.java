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
 * The runner of the execution-behaviour TCK: it reads the scenario files from the classpath, starts an application
 * context for each scenario and compares the JSON responses ({@link io.micronaut.graphql.tools.tck.Tck},
 * {@link io.micronaut.graphql.tools.tck.Scenarios}, {@link io.micronaut.graphql.tools.tck.Scenario}).
 *
 * <p><b>Audience.</b> Test helper of this repository. Not published and not a library API; the three test suites use
 * it.</p>
 */
package io.micronaut.graphql.tools.tck;
