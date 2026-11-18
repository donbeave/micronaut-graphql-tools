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
package io.micronaut.graphql.tools.schema;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Converts one argument or input object field value that graphql-java coerced into the declared Java type. A converter
 * is built once at startup, from the declared type and the GraphQL type, and holds no per-request state.
 *
 * @since 1.0.0
 */
@Internal
@FunctionalInterface
public interface ValueConverter {

    /**
     * @param value the coerced value, may be {@code null}
     * @return the value in the declared Java type, {@code null} for {@code null}
     */
    @Nullable
    Object convert(@Nullable Object value);

}
