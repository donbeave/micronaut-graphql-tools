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
 * The Micronaut executable-method processors that hand each method annotated {@code @GraphQLRootResolver},
 * {@code @GraphQLTypeResolver} or {@code @GraphQLSchemaCoordinate} to the resolvers registry, so that the library finds
 * the resolvers at startup without scanning the classpath.
 *
 * <p><b>Audience.</b> Internal. Not part of the API; do not import it. This is the runtime registry, not the
 * compile-time module {@code graphql-tools-processor}.</p>
 */
@Internal
package io.micronaut.graphql.tools.internal.registry;

import io.micronaut.core.annotation.Internal;
