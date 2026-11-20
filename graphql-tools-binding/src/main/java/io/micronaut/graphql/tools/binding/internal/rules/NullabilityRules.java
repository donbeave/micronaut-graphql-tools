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

import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.model.Nullability;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The rule that compares the nullability of a Kotlin type with the nullability of the schema type at the same position.
 * Only a type whose {@link TypeRef#kotlin()} flag is set takes part: the nullability that a Java or Groovy annotation
 * declares is not compared, so their results do not change.
 *
 * <p>A return type that is nullable at a non-null field, and a parameter or input property that is non-null at a nullable
 * position, fail at run time ({@link BindingCode#KOTLIN_NULLABILITY_MISMATCH}). The opposite is stricter than the schema
 * and never fails by itself ({@link BindingCode#KOTLIN_NULLABILITY_LOOSE}).</p>
 */
@Internal
public final class NullabilityRules {

    // the wrappers that pass the value of the type argument through as it is; Optional and the single-value publishers
    // may be empty, so the schema type they sit at is nullable by the wrapper and says nothing about the argument
    private static final Set<String> TRANSPARENT = Set.of("java.util.concurrent.CompletableFuture",
            "java.util.concurrent.CompletionStage", "graphql.execution.DataFetcherResult");

    private NullabilityRules() {
    }

    /**
     * @param declared   the declared type of a parameter or input property, wrappers peeled
     * @param schemaType the schema type of the argument or input field at the same position
     * @param what       {@code argument} or {@code input field}
     * @return the mismatch, or {@code null} when the types agree or the rule does not apply
     */
    @Nullable
    public static Mismatch input(TypeRef declared, Type<?> schemaType, String what) {
        if (!checked(declared) || declared.primitive()) {
            return null;
        }
        boolean nonNull = schemaType instanceof NonNullType;
        if (declared.nullability() == Nullability.NON_NULL && !nonNull) {
            return new Mismatch(BindingCode.KOTLIN_NULLABILITY_MISMATCH, "the " + what + " is declared non-null ("
                    + simple(declared) + "), but the GraphQL type " + SchemaTypes.print(schemaType)
                    + " is nullable, so null cannot be passed",
                    "declare it nullable (" + simple(declared) + "?) or make the schema type non-null");
        }
        if (declared.nullability() == Nullability.NULLABLE && nonNull) {
            return new Mismatch(BindingCode.KOTLIN_NULLABILITY_LOOSE, "the " + what + " is declared nullable ("
                    + simple(declared) + "?), but the GraphQL type " + SchemaTypes.print(schemaType)
                    + " is non-null, so it is never null",
                    "declare it non-null (" + simple(declared) + ") or make the schema type nullable");
        }
        return null;
    }

    /**
     * Compares the declared return type with the field type, level by level, the way
     * {@link InputBindingFindings#leaf} peels it: through the wrappers that pass the value through, and into the element
     * of each list.
     *
     * @param declared  the declared return type
     * @param fieldType the type of the field in the schema
     * @param verdict   the return adaptation of the field
     * @return the mismatches, outermost first
     */
    public static List<Mismatch> returned(TypeRef declared, Type<?> fieldType, ReturnVerdict verdict) {
        List<Mismatch> result = new ArrayList<>();
        returned(declared, fieldType, verdict, false, result);
        return result;
    }

    private static void returned(TypeRef declared, Type<?> fieldType, ReturnVerdict verdict, boolean element,
                                 List<Mismatch> result) {
        if (verdict.failure() != null || verdict.primitiveOptional() || verdict.reactive() == ReturnVerdict.Reactive.VALUELESS) {
            return;
        }
        TypeRef adapted = declared;
        boolean transparent = verdict.reactive() == ReturnVerdict.Reactive.NONE;
        for (int i = 0; i < verdict.depth(); i++) {
            transparent &= TRANSPARENT.contains(adapted.rawName());
            if (adapted.typeArguments().isEmpty()) {
                return;
            }
            adapted = adapted.typeArguments().get(0);
        }
        if (transparent && checked(adapted)) {
            Mismatch mismatch = returned(adapted, fieldType, element);
            if (mismatch != null) {
                result.add(mismatch);
            }
        }
        Type<?> unwrapped = fieldType instanceof NonNullType nonNull ? nonNull.getType() : fieldType;
        if (!(unwrapped instanceof ListType list) || verdict.element() == null) {
            return;
        }
        if (verdict.reactive() == ReturnVerdict.Reactive.MULTI) {
            returned(adapted, list.getType(), verdict.element(), true, result);
        } else if (!adapted.typeArguments().isEmpty()) {
            returned(adapted.typeArguments().get(0), list.getType(), verdict.element(), true, result);
        }
    }

    @Nullable
    private static Mismatch returned(TypeRef declared, Type<?> schemaType, boolean element) {
        boolean nonNull = schemaType instanceof NonNullType;
        String what = element ? "list element" : "return type";
        if (declared.nullability() == Nullability.NULLABLE && nonNull) {
            return new Mismatch(BindingCode.KOTLIN_NULLABILITY_MISMATCH, "the " + what + " is declared nullable ("
                    + simple(declared) + "?), but the GraphQL type " + SchemaTypes.print(schemaType)
                    + " is non-null, so a null result nulls out the parent",
                    "declare it non-null (" + simple(declared) + ") or make the schema type nullable");
        }
        if (declared.nullability() == Nullability.NON_NULL && !nonNull) {
            return new Mismatch(BindingCode.KOTLIN_NULLABILITY_LOOSE, "the " + what + " is declared non-null ("
                    + simple(declared) + "), but the GraphQL type " + SchemaTypes.print(schemaType)
                    + " is nullable, so the schema promises less than the code returns",
                    "make the schema type non-null or declare the " + what + " nullable (" + simple(declared) + "?)");
        }
        return null;
    }

    private static boolean checked(TypeRef type) {
        return type.kotlin() && !type.array() && type.nullability() != Nullability.UNKNOWN;
    }

    private static String simple(TypeRef type) {
        String name = type.rawName();
        return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
    }

    /**
     * A mismatch that was found.
     *
     * @param code     {@link BindingCode#KOTLIN_NULLABILITY_MISMATCH} or {@link BindingCode#KOTLIN_NULLABILITY_LOOSE}
     * @param sentence what is wrong, without the member
     * @param fix      how to resolve it
     */
    public record Mismatch(BindingCode code, String sentence, String fix) {
    }
}
