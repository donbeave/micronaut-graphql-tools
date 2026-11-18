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
 * The runtime machinery that turns resolver beans into graphql-java wiring: the schema discovery, the data fetchers,
 * the type resolvers and the conversion of argument and input values.
 *
 * <p><b>Audience.</b> Internal. Not part of the API; do not import it. It is being split by responsibility and moved
 * under {@code io.micronaut.graphql.tools.internal} (see the guide page "API Map").</p>
 */
@Internal
package io.micronaut.graphql.tools.schema;

import io.micronaut.core.annotation.Internal;
