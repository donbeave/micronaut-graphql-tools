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
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.Optional;

/**
 * The annotations of an element, with the meaning of Micronaut's annotation metadata: stereotypes are included.
 */
@Internal
public interface AnnotationView {

    /**
     * @param annotation the binary name of an annotation
     * @return whether the element carries it, directly or as a stereotype
     */
    boolean has(String annotation);

    /**
     * @param annotation the binary name of an annotation
     * @param member     the member name
     * @return the string value of the member
     */
    Optional<String> stringValue(String annotation, String member);

    /**
     * @param annotation the binary name of an annotation
     * @param member     the member name
     * @return the binary names of the classes in a class-valued member
     */
    List<String> classValues(String annotation, String member);

    /**
     * @param annotation  the binary name of an annotation
     * @param member      the member that holds an array of annotations
     * @param classMember the member of each of those annotations that holds a class
     * @param nameMember  the member of each of those annotations that holds a name
     * @return the class and the name of each annotation of the member, in order; empty when the element has none
     */
    default List<NamedClass> namedClasses(String annotation, String member, String classMember, String nameMember) {
        return List.of();
    }

    /**
     * @return a view of an element without annotations
     */
    static AnnotationView none() {
        return NoAnnotations.INSTANCE;
    }

    /**
     * A class that an annotation names, with the name it is known by.
     *
     * @param className the binary name of the class
     * @param name      the name, empty when the annotation gives none
     */
    record NamedClass(String className, String name) {
    }
}
