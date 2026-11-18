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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class JavaTypes {

    private final Elements elements;
    private final Types types;
    private final String classOutput;

    JavaTypes(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.classOutput = classOutput(processingEnv);
    }

    private static String classOutput(ProcessingEnvironment processingEnv) {
        try {
            String probe = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", "probe").toUri()
                    .toString();
            return probe.substring(0, probe.length() - "probe".length());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    boolean isInClassOutput(TypeElement type) {
        if (classOutput == null) {
            return false;
        }
        try {
            JavaFileObject file = elements.getFileObjectOf(type);
            return file != null && file.getKind() == JavaFileObject.Kind.CLASS
                    && file.toUri().toString().startsWith(classOutput);
        } catch (RuntimeException e) {
            return false;
        }
    }

    Elements elements() {
        return elements;
    }

    Types types() {
        return types;
    }

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

    List<TypeElement> superclasses(TypeElement type) {
        List<TypeElement> result = new ArrayList<>();
        TypeMirror superclass = type.getSuperclass();
        while (superclass instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement element
                && !element.getQualifiedName().contentEquals("java.lang.Object")) {
            result.add(element);
            superclass = element.getSuperclass();
        }
        return result;
    }

    String name(TypeElement type) {
        return elements.getBinaryName(type).toString();
    }

    String erasedName(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return type.toString();
        }
        TypeMirror erasure = types.erasure(type);
        if (erasure instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement element) {
            return name(element);
        }
        return erasure.toString();
    }

    TypeElement element(TypeMirror type) {
        TypeMirror erasure = types.erasure(type);
        if (erasure instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement element) {
            return element;
        }
        return null;
    }

    boolean isA(TypeMirror type, String className) {
        TypeElement element = elements.getTypeElement(className);
        if (element == null || type.getKind().isPrimitive()) {
            return false;
        }
        return types.isAssignable(types.erasure(type), types.erasure(element.asType()));
    }

    boolean is(TypeMirror type, String className) {
        TypeElement element = element(type);
        return element != null && element.getQualifiedName().contentEquals(className);
    }

    TypeMirror classType(String className) {
        return elements.getTypeElement(className).asType();
    }

    TypeMirror firstTypeArgument(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType) || declaredType.getTypeArguments().isEmpty()) {
            return null;
        }
        TypeMirror argument = declaredType.getTypeArguments().get(0);
        if (argument instanceof WildcardType wildcard) {
            return wildcard.getExtendsBound() != null ? wildcard.getExtendsBound()
                    : elements.getTypeElement("java.lang.Object").asType();
        }
        return argument;
    }

    ExecutableType memberType(TypeElement owner, ExecutableElement method) {
        if (owner.asType() instanceof DeclaredType declaredType) {
            try {
                return (ExecutableType) types.asMemberOf(declaredType, method);
            } catch (IllegalArgumentException e) {
            }
        }
        return (ExecutableType) method.asType();
    }

    String signature(ExecutableElement method) {
        return method.getSimpleName() + "(" + method.getParameters().stream()
                .map(it -> erasedName(it.asType()))
                .collect(Collectors.joining(", ")) + ")";
    }

    String describe(ExecutableElement method) {
        return name((TypeElement) method.getEnclosingElement()) + "#" + signature(method);
    }

    boolean isResolver(TypeElement type) {
        return hierarchy(type).stream().anyMatch(it -> Annotations.has(it, Annotations.ROOT_RESOLVER)
                || Annotations.has(it, Annotations.TYPE_RESOLVER));
    }

    boolean isGraphQLType(TypeElement type) {
        if (Annotations.has(type, Annotations.TYPE) || Annotations.has(type, Annotations.INPUT)) {
            return true;
        }
        return hierarchy(type).stream().anyMatch(it -> Annotations.has(it, Annotations.ROOT_RESOLVER)
                || Annotations.has(it, Annotations.TYPE_RESOLVER));
    }

}
