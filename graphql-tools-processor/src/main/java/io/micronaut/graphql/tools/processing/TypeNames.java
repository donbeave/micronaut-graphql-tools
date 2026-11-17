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

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Renders Java types for the binding manifest exactly as the library renders them at startup from Micronaut's
 * compile-time type information (an {@code Argument}), never by reflection: binary class names
 * ({@code a.Outer$Inner}), type arguments separated by {@code ", "}, {@code []} for arrays. What an {@code Argument}
 * does not keep is rendered as it does: a wildcard is its bound ({@code List<? extends Book>} reads
 * {@code java.util.List<Book>}, an unbounded one {@code java.lang.Object}), a raw type has {@code java.lang.Object} as
 * each type argument, an array of a parameterized type has no type arguments ({@code List<String>[]} reads
 * {@code java.util.List[]}), and a type variable is its bound. The members are seen from the class that has them, so
 * the type variables of a generic superclass are already replaced.
 */
final class TypeNames {

    private static final String FUTURE = "java.util.concurrent.Future";
    private static final String ITERABLE = "java.lang.Iterable";
    private static final String OBJECT = "java.lang.Object";

    private final JavaTypes javaTypes;

    /**
     * @param javaTypes the type helpers
     */
    TypeNames(JavaTypes javaTypes) {
        this.javaTypes = javaTypes;
    }

    /**
     * @param type a Java type
     * @return the type as the library renders an {@code Argument}
     */
    String render(TypeMirror type) {
        TypeKind kind = type.getKind();
        if (kind.isPrimitive() || kind == TypeKind.VOID) {
            return kind.name().toLowerCase(Locale.ROOT);
        }
        if (type instanceof ArrayType arrayType) {
            return renderErased(arrayType.getComponentType()) + "[]";
        }
        if (type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement element) {
            String raw = javaTypes.name(element);
            List<? extends TypeMirror> arguments = declaredType.getTypeArguments();
            if (arguments.isEmpty()) {
                int parameters = element.getTypeParameters().size();
                return parameters == 0 ? raw
                        : raw + Collections.nCopies(parameters, OBJECT).stream().collect(Collectors.joining(", ", "<", ">"));
            }
            return raw + arguments.stream().map(this::render).collect(Collectors.joining(", ", "<", ">"));
        }
        if (type instanceof TypeVariable typeVariable) {
            return render(typeVariable.getUpperBound());
        }
        if (type instanceof WildcardType wildcard) {
            TypeMirror bound = wildcard.getSuperBound() != null ? wildcard.getSuperBound() : wildcard.getExtendsBound();
            return bound == null ? OBJECT : render(bound);
        }
        return type.toString();
    }

    /**
     * @param type the component type of an array
     * @return the type without type arguments, as an {@code Argument} keeps an array's component
     */
    private String renderErased(TypeMirror type) {
        if (type instanceof ArrayType arrayType) {
            return renderErased(arrayType.getComponentType()) + "[]";
        }
        if (type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement element) {
            return javaTypes.name(element);
        }
        return render(type);
    }

    /**
     * @param owner  the class that has the method, which decides the type variables the method inherits
     * @param method a method
     * @return its parameter types, seen from the owner, in parentheses
     */
    String parameters(TypeElement owner, ExecutableElement method) {
        return javaTypes.memberType(owner, method).getParameterTypes().stream()
                .map(this::render)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    /**
     * The converted type of a manifest line, as the library's {@code ReturnTypes.convertedTypeName} computes it from
     * the {@code Argument}: the declared type without {@code CompletionStage} or {@code Future}, then {@code Publisher}
     * (subscription fields only), then {@code DataFetcherResult} (also as the element of a list), then
     * {@code Optional}, each once and only where it is the class of a declared type.
     *
     * @param declaredType the declared type
     * @param subscription whether the field belongs to the subscription type
     * @return the converted type, rendered
     */
    String converted(TypeMirror declaredType, boolean subscription) {
        TypeMirror result = declaredType;
        if (isA(result, BindingChecks.COMPLETION_STAGE) || isA(result, FUTURE)) {
            result = firstTypeArgument(result);
        }
        if (subscription && isA(result, BindingChecks.PUBLISHER)) {
            result = firstTypeArgument(result);
        }
        if (isA(result, BindingChecks.DATA_FETCHER_RESULT)) {
            result = firstTypeArgument(result);
        }
        if (result instanceof DeclaredType && javaTypes.is(result, BindingChecks.OPTIONAL)) {
            result = firstTypeArgument(result);
        }
        if (isA(result, ITERABLE) && result instanceof DeclaredType list && list.getTypeArguments().size() == 1) {
            TypeMirror element = bound(list.getTypeArguments().get(0));
            if (isA(element, BindingChecks.DATA_FETCHER_RESULT) && element instanceof DeclaredType wrapped
                    && !wrapped.getTypeArguments().isEmpty()) {
                return javaTypes.name((TypeElement) list.asElement()) + "<"
                        + render(wrapped.getTypeArguments().get(0)) + ">";
            }
        }
        return render(result);
    }

    private boolean isA(TypeMirror type, String className) {
        return type instanceof DeclaredType && javaTypes.isA(type, className);
    }

    /**
     * @return the first type argument, a wildcard being its bound, or the type itself if it has none, as the library
     * unwraps an {@code Argument}
     */
    private static TypeMirror firstTypeArgument(TypeMirror type) {
        if (type instanceof DeclaredType declaredType && !declaredType.getTypeArguments().isEmpty()) {
            return bound(declaredType.getTypeArguments().get(0));
        }
        return type;
    }

    private static TypeMirror bound(TypeMirror type) {
        if (type instanceof WildcardType wildcard) {
            TypeMirror bound = wildcard.getSuperBound() != null ? wildcard.getSuperBound() : wildcard.getExtendsBound();
            return bound == null ? type : bound;
        }
        return type;
    }

}
