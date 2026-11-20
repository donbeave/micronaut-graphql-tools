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
 * @param reactive          how a {@code Publisher} on a field outside the subscription type is adapted to a
 *                          {@code CompletionStage}: {@link Reactive#NONE} for every other return type
 * @param async             whether the method returns its value later, as a {@code CompletionStage}, as a publisher
 *                          that is adapted to one, or as a Kotlin {@code suspend} function, on a field outside the
 *                          subscription type: the field is then executed through the async boundary
 * @param element           for a list field, the verdict for the element type, which the runtime checks one level
 *                          down with {@code fieldLevel} false; {@code null} otherwise
 */
@Internal
public record ReturnVerdict(int depth, boolean primitiveOptional, @Nullable String failure, Reactive reactive,
                            boolean async, @Nullable ReturnVerdict element) {

    /**
     * How a {@code Publisher} that a field outside the subscription type returns is adapted, decided once per field.
     */
    public enum Reactive {
        /** The return type is not adapted. */
        NONE,
        /** A publisher of one value ({@code Mono}): the value, or {@code null} when it is empty. */
        SINGLE,
        /** A publisher of many values ({@code Flux}) on a list field: the values collected into a list. */
        MULTI,
        /** A single-value type that emits no value ({@code Completable}): the field is {@code null}, nothing is unwrapped. */
        VALUELESS
    }

    static ReturnVerdict failed(String failure) {
        return new ReturnVerdict(0, false, failure, Reactive.NONE, false, null);
    }

    /**
     * @return the sentence for the first violation at this level or below, or {@code null} when there is none
     */
    @Nullable
    public String firstFailure() {
        if (failure != null) {
            return failure;
        }
        return element == null ? null : element.firstFailure();
    }
}
