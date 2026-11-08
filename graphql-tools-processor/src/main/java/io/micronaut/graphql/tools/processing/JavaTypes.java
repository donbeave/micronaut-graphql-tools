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

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helpers for the Java types of the compilation and of its classpath.
 */
final class JavaTypes {

    private final Elements elements;
    private final Types types;

    /**
     * @param processingEnv the processing environment
     */
    JavaTypes(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    /**
     * @return the element utilities
     */
    Elements elements() {
        return elements;
    }

    /**
     * @return the type utilities
     */
    Types types() {
        return types;
    }

    /**
     * @param type a type
     * @return the type and all its supertypes, classes and interfaces, also those of the classpath, nearest first,
     * without {@code java.lang.Object}
     */
    List<TypeElement> hierarchy(TypeElement type) {
        List<TypeElement> result = new ArrayList<>();
        Deque<TypeMirror> pending = new ArrayDeque<>(List.of(type.asType()));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            TypeMirror mirror = pending.poll();
            if (!(mirror instanceof DeclaredType declaredType)
                    || !(declaredType.asElement() instanceof TypeElement element)) {
                continue;
            }
            String name = element.getQualifiedName().toString();
            if (name.equals("java.lang.Object") || !visited.add(name)) {
                continue;
            }
            result.add(element);
            pending.addAll(types.directSupertypes(mirror));
        }
        return result;
    }

    /**
     * @param type a type
     * @return whether the type is something the processor checks: a class or interface that is a root or type
     * resolver (directly or through a supertype), or a class annotated with {@code @GraphQLType} or
     * {@code @GraphQLInput}
     */
    boolean isGraphQLType(TypeElement type) {
        if (Annotations.has(type, Annotations.TYPE) || Annotations.has(type, Annotations.INPUT)) {
            return true;
        }
        return hierarchy(type).stream().anyMatch(it -> Annotations.has(it, Annotations.ROOT_RESOLVER)
                || Annotations.has(it, Annotations.TYPE_RESOLVER));
    }

}
