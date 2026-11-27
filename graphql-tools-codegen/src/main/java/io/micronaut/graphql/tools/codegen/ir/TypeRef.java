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
package io.micronaut.graphql.tools.codegen.ir;

import java.util.Objects;

/**
 * A reference to a type, with the nullability of the position that it is in: a {@code nullable} reference is a GraphQL type
 * without {@code !}. The IR keeps the nullability exactly; whether a non-null {@code Int}, {@code Float} or {@code Boolean} is a
 * primitive is a rule of the emitter, not a field here.
 */
public sealed interface TypeRef permits TypeRef.Builtin, TypeRef.Mapped, TypeRef.Reference, TypeRef.ListOf {

    /**
     * Whether the position accepts {@code null}.
     *
     * @return {@code true} for a type without {@code !}
     */
    boolean nullable();

    /**
     * A built-in scalar.
     *
     * @param scalar   the scalar
     * @param nullable whether it accepts {@code null}
     */
    record Builtin(BuiltinScalar scalar, boolean nullable) implements TypeRef {

        /**
         * Creates the reference.
         */
        public Builtin {
            Objects.requireNonNull(scalar, "scalar");
        }
    }

    /**
     * A type that the {@code typeMappings} option maps to an external class: a custom scalar, a single field, or a type that the
     * schema defines but that is not generated.
     *
     * @param className the qualified name of the class
     * @param nullable  whether it accepts {@code null}
     */
    record Mapped(String className, boolean nullable) implements TypeRef {

        /**
         * Creates the reference.
         */
        public Mapped {
            Objects.requireNonNull(className, "className");
        }
    }

    /**
     * A type that the generator produces: an enum, an input, a model, an interface or a union.
     *
     * @param target   the names of the referenced type
     * @param nullable whether it accepts {@code null}
     */
    record Reference(Names target, boolean nullable) implements TypeRef {

        /**
         * Creates the reference.
         */
        public Reference {
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * A list.
     *
     * @param element  the type of the elements, with its own nullability
     * @param nullable whether the list accepts {@code null}
     */
    record ListOf(TypeRef element, boolean nullable) implements TypeRef {

        /**
         * Creates the reference.
         */
        public ListOf {
            Objects.requireNonNull(element, "element");
        }
    }
}
