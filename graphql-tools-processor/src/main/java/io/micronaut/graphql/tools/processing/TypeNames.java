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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Renders Java types for the binding manifest exactly as the library renders them at startup from the reflected
 * members, that is as {@link java.lang.reflect.Type#getTypeName()} renders the generic type of the declaration: binary
 * class names ({@code a.Outer$Inner}), type arguments separated by {@code ", "}, wildcards ({@code ? extends T},
 * {@code ? super T}, {@code ?}), type variables by name, {@code []} for arrays. The types are those the member declares
 * where it is written, not as a subclass sees it, as reflection gives them.
 */
final class TypeNames {

    private static final String FUTURE = "java.util.concurrent.Future";
    private static final String ITERABLE = "java.lang.Iterable";

    private final JavaTypes javaTypes;

    /**
     * @param javaTypes the type helpers
     */
    TypeNames(JavaTypes javaTypes) {
        this.javaTypes = javaTypes;
    }

    /**
     * @param type a Java type
     * @return the type in {@link java.lang.reflect.Type#getTypeName()} form
     */
    String render(TypeMirror type) {
        TypeKind kind = type.getKind();
        if (kind.isPrimitive() || kind == TypeKind.VOID) {
            return kind.name().toLowerCase(Locale.ROOT);
        }
        if (type instanceof ArrayType arrayType) {
            return render(arrayType.getComponentType()) + "[]";
        }
        if (type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement element) {
            List<? extends TypeMirror> arguments = declaredType.getTypeArguments();
            if (arguments.isEmpty()) {
                return javaTypes.name(element);
            }
            String raw = javaTypes.name(element);
            if (declaredType.getEnclosingType() instanceof DeclaredType owner && !owner.getTypeArguments().isEmpty()) {
                // as reflection renders an inner class of a parameterized class: Outer<A>$Inner<B>
                raw = render(owner) + "$" + element.getSimpleName();
            }
            return raw + arguments.stream().map(this::render).collect(Collectors.joining(", ", "<", ">"));
        }
        if (type instanceof TypeVariable typeVariable) {
            return typeVariable.asElement().getSimpleName().toString();
        }
        if (type instanceof WildcardType wildcard) {
            if (wildcard.getSuperBound() != null) {
                return "? super " + render(wildcard.getSuperBound());
            }
            TypeMirror bound = wildcard.getExtendsBound();
            boolean object = bound instanceof DeclaredType declared
                    && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals("java.lang.Object");
            if (bound != null && !object) {
                return "? extends " + render(bound);
            }
            return "?";
        }
        return type.toString();
    }

    /**
     * @param method a method
     * @return its generic parameter types, rendered
     */
    String parameters(ExecutableElement method) {
        return method.getParameters().stream()
                .map(it -> render(it.asType()))
                .collect(Collectors.joining(", ", "(", ")"));
    }

    /**
     * The converted type of a manifest line, as the library's {@code ReturnTypes.convertedTypeName} computes it from
     * the reflected type: the declared type without {@code CompletionStage} or {@code Future}, then {@code Publisher}
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
            TypeMirror element = list.getTypeArguments().get(0);
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
     * @return the first type argument as it is written (a wildcard stays a wildcard), or the type itself if it has
     * none, as the library's reflective unwrapping does
     */
    private static TypeMirror firstTypeArgument(TypeMirror type) {
        if (type instanceof DeclaredType declaredType && !declaredType.getTypeArguments().isEmpty()) {
            return declaredType.getTypeArguments().get(0);
        }
        return type;
    }

}
