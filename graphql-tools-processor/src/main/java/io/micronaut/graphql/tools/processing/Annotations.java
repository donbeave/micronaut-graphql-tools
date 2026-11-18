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
package io.micronaut.graphql.tools.processing;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class Annotations {

    static final String ROOT_RESOLVER = "io.micronaut.graphql.tools.annotation.GraphQLRootResolver";
    static final String TYPE_RESOLVER = "io.micronaut.graphql.tools.annotation.GraphQLTypeResolver";
    static final String TYPE = "io.micronaut.graphql.tools.annotation.GraphQLType";
    static final String INPUT = "io.micronaut.graphql.tools.annotation.GraphQLInput";
    static final String FIELD = "io.micronaut.graphql.tools.annotation.GraphQLField";
    static final String SCHEMA_COORDINATE = "io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate";
    static final String INTROSPECTED = "io.micronaut.core.annotation.Introspected";

    static final Set<String> INTRODUCTION = Set.of("io.micronaut.aop.Introduction");

    static final Set<String> BEAN_DEFINING = Set.of(
            "jakarta.inject.Singleton",
            "jakarta.inject.Scope",
            "io.micronaut.context.annotation.Bean",
            "io.micronaut.context.annotation.Prototype",
            "io.micronaut.context.annotation.Context",
            "io.micronaut.context.annotation.Infrastructure",
            "io.micronaut.runtime.context.scope.Refreshable",
            "io.micronaut.runtime.context.scope.ThreadLocal",
            "io.micronaut.runtime.http.scope.RequestScope"
    );

    static final Set<String> CLAIMED;

    static {
        Set<String> claimed = new HashSet<>(BEAN_DEFINING);
        claimed.remove("jakarta.inject.Scope");
        claimed.addAll(Set.of(ROOT_RESOLVER, TYPE_RESOLVER, TYPE, INPUT, FIELD, SCHEMA_COORDINATE, INTROSPECTED));
        CLAIMED = Set.copyOf(claimed);
    }

    private Annotations() {
    }

    static Optional<AnnotationMirror> find(Element element, String annotation) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (name(mirror).equals(annotation)) {
                return Optional.of(mirror);
            }
        }
        return Optional.empty();
    }

    static boolean has(Element element, String annotation) {
        return find(element, annotation).isPresent();
    }

    static boolean hasStereotype(Element element, Set<String> annotations) {
        return hasStereotype(element, annotations, new HashSet<>());
    }

    private static boolean hasStereotype(Element element, Set<String> annotations, Set<String> visited) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String name = name(mirror);
            if (annotations.contains(name)) {
                return true;
            }
            if (!name.startsWith("java.lang.") && visited.add(name)
                    && hasStereotype(mirror.getAnnotationType().asElement(), annotations, visited)) {
                return true;
            }
        }
        return false;
    }

    static Optional<TypeElement> classValue(AnnotationMirror mirror) {
        Object value = value(mirror);
        if (value instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement type) {
            return Optional.of(type);
        }
        return Optional.empty();
    }

    static Optional<String> stringValue(AnnotationMirror mirror) {
        return value(mirror) instanceof String string ? Optional.of(string) : Optional.empty();
    }

    static Optional<TypeMirror> typeValue(AnnotationMirror mirror) {
        return value(mirror) instanceof TypeMirror type ? Optional.of(type) : Optional.empty();
    }

    static List<String> classValues(AnnotationMirror mirror, String member) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(member)
                    && entry.getValue().getValue() instanceof List<?> values) {
                for (Object value : values) {
                    if (value instanceof AnnotationValue annotationValue
                            && annotationValue.getValue() instanceof DeclaredType declaredType
                            && declaredType.asElement() instanceof TypeElement type) {
                        result.add(type.getQualifiedName().toString());
                    }
                }
            }
        }
        return result;
    }

    static List<String> stringValues(AnnotationMirror mirror, String member) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(member)) {
                Object value = entry.getValue().getValue();
                List<?> values = value instanceof List<?> list ? list : List.of(entry.getValue());
                for (Object item : values) {
                    Object itemValue = item instanceof AnnotationValue annotationValue ? annotationValue.getValue() : item;
                    if (itemValue instanceof VariableElement constant) {
                        result.add(constant.getSimpleName().toString());
                    } else if (itemValue instanceof String string) {
                        result.add(string);
                    }
                }
            }
        }
        return result;
    }

    private static Object value(AnnotationMirror mirror) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals("value")) {
                return entry.getValue().getValue();
            }
        }
        return null;
    }

    private static String name(AnnotationMirror mirror) {
        return ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().toString();
    }

}
