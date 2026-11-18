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
package io.micronaut.graphql.tools.binding.internal.rules;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * What the wrappers of a Java return type, checked against the type of the schema field, leave behind.
 *
 * @param depth             how many times the type argument has to be taken to reach the adapted type (one per
 *                          {@code CompletionStage}, {@code Publisher}, {@code DataFetcherResult} and {@code Optional}
 *                          that was peeled)
 * @param primitiveOptional whether the adapted type is the boxed type inside an {@code OptionalInt},
 *                          {@code OptionalLong} or {@code OptionalDouble}, which is then the type that was reached
 * @param failure           the sentence for the first violation, or {@code null} when the wrappers are valid
 */
@Internal
public record ReturnVerdict(int depth, boolean primitiveOptional, @Nullable String failure) {

    static ReturnVerdict failed(String failure) {
        return new ReturnVerdict(0, false, failure);
    }
}
