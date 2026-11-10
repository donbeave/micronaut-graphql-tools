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

/**
 * Helpers for the Java types of the compilation and of its classpath.
 */
final class JavaTypes {

    private final Elements elements;
    private final Types types;
    private final String classOutput;

    /**
     * @param processingEnv the processing environment
     */
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

    /**
     * Whether a class belongs to the module being compiled although it is not a source of this compilation: an
     * incremental compilation (Gradle) compiles only the changed sources and reads the unchanged classes of the module
     * from the class output, where the processor finds them.
     *
     * @param type a class
     * @return whether its class file is in the class output of this compilation
     */
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
     * @param type a class
     * @return its superclasses, nearest first, without {@code java.lang.Object}
     */
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

    /**
     * @param type a type
     * @return its binary name, as {@link Class#getName()} gives it ({@code a.Outer$Inner})
     */
    String name(TypeElement type) {
        return elements.getBinaryName(type).toString();
    }

    /**
     * @param type a Java type
     * @return the binary name of its erasure, or the name of a primitive type; for an array its type name
     */
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

    /**
     * @param type a Java type
     * @return its class, or {@code null} for a primitive, an array or a type variable
     */
    TypeElement element(TypeMirror type) {
        TypeMirror erasure = types.erasure(type);
        if (erasure instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement element) {
            return element;
        }
        return null;
    }

    /**
     * @param type      a Java type
     * @param className the qualified name of a class or interface
     * @return whether the type is that class or interface or a subtype of it; {@code false} if the class is not on the
     * classpath
     */
    boolean isA(TypeMirror type, String className) {
        TypeElement element = elements.getTypeElement(className);
        if (element == null || type.getKind().isPrimitive()) {
            return false;
        }
        return types.isAssignable(types.erasure(type), types.erasure(element.asType()));
    }

    /**
     * @param type      a Java type
     * @param className the qualified name of a class
     * @return whether the erasure of the type is that class
     */
    boolean is(TypeMirror type, String className) {
        TypeElement element = element(type);
        return element != null && element.getQualifiedName().contentEquals(className);
    }

    /**
     * The first type argument of a type, as Micronaut's {@code Argument#getFirstTypeVariable()} gives it: of the
     * declared type itself, not of a supertype; the upper bound of a wildcard.
     *
     * @param type a Java type
     * @return the first type argument, or {@code null} if the type declares none
     */
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

    /**
     * The type of a method as a member of a class: the type variables of a generic superclass or interface that the
     * class binds are replaced, as Micronaut resolves them for the bean or the introspection ({@code List<T>} of a
     * {@code Base<T>} is {@code List<Book>} in a {@code BookQuery extends Base<Book>}).
     *
     * @param owner  the class, which declares or inherits the method
     * @param method the method
     * @return the method type as seen from the class
     */
    ExecutableType memberType(TypeElement owner, ExecutableElement method) {
        if (owner.asType() instanceof DeclaredType declaredType) {
            try {
                return (ExecutableType) types.asMemberOf(declaredType, method);
            } catch (IllegalArgumentException e) {
                // not a member of the class: its own declaration
            }
        }
        return (ExecutableType) method.asType();
    }

    /**
     * @param method a method
     * @return its name and the erased types of its parameters, as {@code name(java.lang.String, int)}
     */
    String signature(ExecutableElement method) {
        return method.getSimpleName() + "(" + method.getParameters().stream()
                .map(it -> erasedName(it.asType()))
                .collect(Collectors.joining(", ")) + ")";
    }

    /**
     * @param method a method
     * @return the binary name of its class, {@code #} and its signature
     */
    String describe(ExecutableElement method) {
        return name((TypeElement) method.getEnclosingElement()) + "#" + signature(method);
    }

    /**
     * @param type a type
     * @return whether it is a root or type resolver, directly or through a supertype (a bean or a contract)
     */
    boolean isResolver(TypeElement type) {
        return hierarchy(type).stream().anyMatch(it -> Annotations.has(it, Annotations.ROOT_RESOLVER)
                || Annotations.has(it, Annotations.TYPE_RESOLVER));
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
