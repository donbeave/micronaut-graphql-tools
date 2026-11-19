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
 * The entry points of the binding at startup: the factories that create the {@code GraphQLSchema} and the
 * {@code GraphQL} beans, the eager binding, the selection of the library's beans against the application's own and
 * the helpers that the sub-packages share.
 *
 * <p><b>Audience.</b> Internal. Not part of the API; do not import it. It can change or move in any release. The
 * implementation is split by responsibility into the sub-packages {@code checks}, {@code discovery}, {@code input},
 * {@code manifest}, {@code model}, {@code registry}, {@code resolver} and {@code wiring}; see the guide page
 * "API Map".</p>
 */
@Internal
package io.micronaut.graphql.tools.internal;

import io.micronaut.core.annotation.Internal;
