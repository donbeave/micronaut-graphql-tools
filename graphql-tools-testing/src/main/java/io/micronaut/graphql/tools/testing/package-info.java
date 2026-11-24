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
 * The in-process tester: {@link io.micronaut.graphql.tools.testing.GraphQLTester} runs a document with variables, a context and
 * headers through the application's GraphQL invocation and returns a
 * {@link io.micronaut.graphql.tools.testing.GraphQLTestResponse} with data, errors and extensions and fluent assertions on paths
 * and errors.
 *
 * <p><b>Audience.</b> Public API, for test code. An application never uses it in production code.</p>
 */
package io.micronaut.graphql.tools.testing;
