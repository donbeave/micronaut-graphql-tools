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
        TypeRef type = field.type();
        if (type instanceof TypeRef.Builtin builtin && !builtin.nullable() && !field.boxed()) {
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

    private TypeName reference(TypeRef type) {
        TypeName name;
        if (type instanceof TypeRef.Builtin builtin) {
            name = switch (builtin.scalar()) {
                case ID, STRING -> ClassName.get(String.class);
                case INT -> ClassName.get(Integer.class);
                case FLOAT -> ClassName.get(Double.class);
                case BOOLEAN -> ClassName.get(Boolean.class);
            };
        } else if (type instanceof TypeRef.Mapped mapped) {
            name = ClassName.bestGuess(mapped.className());
        } else if (type instanceof TypeRef.Reference reference) {
            name = ClassName.get(reference.target().packageName(), reference.target().simpleName());
        } else {
            name = ParameterizedTypeName.get(ClassName.get(java.util.List.class), reference(((TypeRef.ListOf) type).element()));
        }
        return type.isNullable() && nullable != null ? name.annotated(nullable) : name;
    }
}
