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
package io.micronaut.graphql.tools.binding.internal.finding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.core.annotation.Nullable;

/**
 * One finding of a rule, before it is rendered.
 *
 * @param code           the code of the registry ({@link BindingCode#getCode()})
 * @param coordinate     the schema coordinate, or {@code null} when the finding has none
 * @param sentence       one sentence that says what is wrong
 * @param member         the Java member as {@code binary.Name#member(parameter types)}, or {@code null}
 * @param declaredIn     the binary name of the contract that declares an inherited member, or {@code null}
 * @param schemaLocation where the schema defines the coordinate, or {@code null}
 * @param fix            the change or hatch that resolves it, or {@code null}
 * @param documentLocation where a client document has the problem, or {@code null}
 */
@Internal
public record Finding(String code, @Nullable String coordinate, String sentence, @Nullable String member,
                      @Nullable String declaredIn, @Nullable SchemaLocation schemaLocation, @Nullable String fix,
                      @Nullable DocumentLocation documentLocation) {

    public Finding {
        if (BindingCode.fromCode(code) == null) {
            throw new IllegalArgumentException("Not a code of the registry: " + code);
        }
    }

    /**
     * A finding that has no position in a client document.
     *
     * @param code           the code of the registry
     * @param coordinate     the schema coordinate, or {@code null} when the finding has none
     * @param sentence       one sentence that says what is wrong
     * @param member         the Java member, or {@code null}
     * @param declaredIn     the binary name of the contract that declares an inherited member, or {@code null}
     * @param schemaLocation where the schema defines the coordinate, or {@code null}
     * @param fix            the change or hatch that resolves it, or {@code null}
     */
    public Finding(String code, @Nullable String coordinate, String sentence, @Nullable String member,
                   @Nullable String declaredIn, @Nullable SchemaLocation schemaLocation, @Nullable String fix) {
        this(code, coordinate, sentence, member, declaredIn, schemaLocation, fix, null);
    }

    /**
     * @param code       the check code
     * @param coordinate the schema coordinate, or {@code null}
     * @param sentence   one sentence that says what is wrong
     * @return a finding with no member, schema location or fix
     */
    public static Finding of(String code, @Nullable String coordinate, String sentence) {
        return new Finding(code, coordinate, sentence, null, null, null, null);
    }
}
