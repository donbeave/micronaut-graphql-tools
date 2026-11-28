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
package io.micronaut.graphql.tools.codegen;

import io.micronaut.graphql.tools.codegen.ir.Field;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;
import io.micronaut.sourcegen.javapoet.AnnotationSpec;
import io.micronaut.sourcegen.javapoet.ClassName;
import io.micronaut.sourcegen.javapoet.ParameterizedTypeName;
import io.micronaut.sourcegen.javapoet.TypeName;

/**
 * The Java type of an IR type reference. A non-null {@code Int}, {@code Float} or {@code Boolean} is a primitive in a direct value
 * position unless the field is boxed; everything else, every list element and every type argument included, is a reference type.
 * A nullable position carries the nullable annotation as a type annotation, and a list is always a {@code java.util.List} of its
 * element.
 */
final class JavaTypes {

    private final AnnotationSpec nullable;

    JavaTypes(String nullableAnnotation) {
        this.nullable = nullableAnnotation.isEmpty() ? null : AnnotationSpec.builder(ClassName.bestGuess(nullableAnnotation)).build();
    }

    TypeName component(Field field) {
        return component(field.type(), field.boxed());
    }

    /**
     * The type of a value in a direct position: a non-null {@code Int}, {@code Float} or {@code Boolean} is a primitive unless it is boxed.
     *
     * @param type  the type of the value
     * @param boxed whether a primitive is written as its wrapper class
     * @return the Java type
     */
    TypeName component(TypeRef type, boolean boxed) {
        if (type instanceof TypeRef.Builtin builtin && !builtin.nullable() && !boxed) {
            switch (builtin.scalar()) {
                case INT:
                    return TypeName.INT;
                case FLOAT:
                    return TypeName.DOUBLE;
                case BOOLEAN:
                    return TypeName.BOOLEAN;
                default:
                    break;
            }
        }
        return reference(type);
    }

    /**
     * The type of a value that may be left out although its GraphQL type is non-null: a reference type annotated as nullable.
     *
     * @param type the type of the value
     * @return the Java type
     */
    TypeName omittable(TypeRef type) {
        return reference(type, true);
    }

    /**
     * The type of a type argument that is not a position of the model: a reference type without the nullable annotation, at every level.
     *
     * @param type the type
     * @return the Java type
     */
    TypeName plain(TypeRef type) {
        if (type instanceof TypeRef.ListOf list) {
            return ParameterizedTypeName.get(ClassName.get(java.util.List.class), plain(list.element()));
        }
        return erased(type);
    }

    TypeName reference(TypeRef type) {
        return reference(type, false);
    }

    private TypeName reference(TypeRef type, boolean forceNullable) {
        TypeName name;
        if (type instanceof TypeRef.ListOf list) {
            name = ParameterizedTypeName.get(ClassName.get(java.util.List.class), reference(list.element(), false));
        } else {
            name = erased(type);
        }
        return (forceNullable || type.isNullable()) && nullable != null ? name.annotated(nullable) : name;
    }

    private static TypeName erased(TypeRef type) {
        if (type instanceof TypeRef.Builtin builtin) {
            return switch (builtin.scalar()) {
                case ID, STRING -> ClassName.get(String.class);
                case INT -> ClassName.get(Integer.class);
                case FLOAT -> ClassName.get(Double.class);
                case BOOLEAN -> ClassName.get(Boolean.class);
            };
        } else if (type instanceof TypeRef.Mapped mapped) {
            return ClassName.bestGuess(mapped.className());
        }
        TypeRef.Reference reference = (TypeRef.Reference) type;
        return ClassName.get(reference.target().packageName(), reference.target().simpleName());
    }
}
