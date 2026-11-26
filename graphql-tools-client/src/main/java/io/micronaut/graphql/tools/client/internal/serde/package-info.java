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
 * The Micronaut Serialization codec of the GraphQL client: the response envelope and the unwrapping of its data. It reads and
 * writes the wire format through the Serde API, and no other JSON library.
 *
 * <p><b>Audience.</b> Internal. Not API.</p>
 */
@Internal
package io.micronaut.graphql.tools.client.internal.serde;

import io.micronaut.core.annotation.Internal;
