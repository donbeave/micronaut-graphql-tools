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
 * Mapping of the exceptions that resolvers throw to GraphQL errors: {@link io.micronaut.graphql.tools.error.GraphQLExceptionMapper}.
 *
 * <p>This package is the error-handling SPI. The types the library itself throws are in
 * {@code io.micronaut.graphql.tools.exceptions}.</p>
 *
 * <p><b>Audience.</b> Public API. Application code implements the mapper for each exception type whose message and
 * extensions the client may see, and uses the {@link io.micronaut.graphql.tools.error.GraphQLExceptionContext} to build the
 * error with the path and the location of the field.</p>
 */
package io.micronaut.graphql.tools.error;
