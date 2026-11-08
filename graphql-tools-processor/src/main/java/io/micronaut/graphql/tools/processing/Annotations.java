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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The annotations the processor reads, by name: the processor does not depend on the library or on Micronaut, so
 * that it runs on any processor path.
 */
final class Annotations {

    static final String ROOT_RESOLVER = "io.micronaut.graphql.tools.annotation.GraphQLRootResolver";
    static final String TYPE_RESOLVER = "io.micronaut.graphql.tools.annotation.GraphQLTypeResolver";
    static final String TYPE = "io.micronaut.graphql.tools.annotation.GraphQLType";
    static final String INPUT = "io.micronaut.graphql.tools.annotation.GraphQLInput";
    static final String FIELD = "io.micronaut.graphql.tools.annotation.GraphQLField";
    static final String SCHEMA_COORDINATE = "io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate";
    static final String INTROSPECTED = "io.micronaut.core.annotation.Introspected";

    /**
     * The annotations that make a class a Micronaut bean, directly or as a meta-annotation of a stereotype.
     */
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

    /**
     * The annotations the processor claims: those of the library and the bean-defining ones, so that Gradle hands every
     * resolver bean and model class to it again on an incremental compilation. Never {@code *}, which would make every
     * compilation a full one.
     */
    static final Set<String> CLAIMED;

    static {
        Set<String> claimed = new HashSet<>(BEAN_DEFINING);
        claimed.remove("jakarta.inject.Scope");
        claimed.addAll(Set.of(ROOT_RESOLVER, TYPE_RESOLVER, TYPE, INPUT, FIELD, SCHEMA_COORDINATE, INTROSPECTED));
        CLAIMED = Set.copyOf(claimed);
    }

    private Annotations() {
    }

    /**
     * @param element    an element
     * @param annotation an annotation name
     * @return the annotation if the element carries it directly
     */
    static Optional<AnnotationMirror> find(Element element, String annotation) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (name(mirror).equals(annotation)) {
                return Optional.of(mirror);
            }
        }
        return Optional.empty();
    }

    /**
     * @param element    an element
     * @param annotation an annotation name
     * @return whether the element carries the annotation directly
     */
    static boolean has(Element element, String annotation) {
        return find(element, annotation).isPresent();
    }

    /**
     * @param element     an element
     * @param annotations annotation names
     * @return whether the element carries one of the annotations, directly or as a meta-annotation of an annotation it
     * carries (at any depth)
     */
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

    /**
     * @param mirror an annotation
     * @return the value of its {@code value} member if it is a class, otherwise empty
     */
    static Optional<TypeElement> classValue(AnnotationMirror mirror) {
        Object value = value(mirror);
        if (value instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement type) {
            return Optional.of(type);
        }
        return Optional.empty();
    }

    /**
     * @param mirror an annotation
     * @return the value of its {@code value} member if it is a string, otherwise empty
     */
    static Optional<String> stringValue(AnnotationMirror mirror) {
        return value(mirror) instanceof String string ? Optional.of(string) : Optional.empty();
    }

    /**
     * @param mirror an annotation
     * @return the value of its {@code value} member if it is a class, as a type, otherwise empty
     */
    static Optional<TypeMirror> typeValue(AnnotationMirror mirror) {
        return value(mirror) instanceof TypeMirror type ? Optional.of(type) : Optional.empty();
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
