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
 * Kotlin {@code suspend} resolvers: the bean that starts the coroutine of a {@code suspend} method and hands the result
 * to the async field boundary as a {@code CompletionStage}. The bean exists only when Kotlin and kotlinx-coroutines-core
 * are on the classpath.
 *
 * <p><b>Audience.</b> Internal. Not part of the API; do not import it. Key types: KotlinSuspendInvoker.</p>
 */
@Internal
package io.micronaut.graphql.tools.internal.kotlin;

import io.micronaut.core.annotation.Internal;
