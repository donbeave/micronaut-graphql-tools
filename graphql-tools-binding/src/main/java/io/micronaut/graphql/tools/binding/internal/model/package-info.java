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
 * The model of Java classes, members, parameters, annotations and type references that the rules read. The runtime
 * and the compile-time visitor each implement {@code TypeModel} over their own source of truth, so the rules never
 * read the Micronaut inject API.
 *
 * <p><b>Audience.</b> Internal; do not import it from application code.</p>
 */
@Internal
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;
