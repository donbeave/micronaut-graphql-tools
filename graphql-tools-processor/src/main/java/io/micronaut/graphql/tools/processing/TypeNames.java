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

final class TypeNames {

    private static final String FUTURE = "java.util.concurrent.Future";
    private static final String ITERABLE = "java.lang.Iterable";
    private static final String OBJECT = "java.lang.Object";

    private final JavaTypes javaTypes;

    TypeNames(JavaTypes javaTypes) {
        this.javaTypes = javaTypes;
    }

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

    private String renderErased(TypeMirror type) {
        if (type instanceof ArrayType arrayType) {
            return renderErased(arrayType.getComponentType()) + "[]";
        }
        if (type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement element) {
            return javaTypes.name(element);
        }
        return render(type);
    }

    String parameters(TypeElement owner, ExecutableElement method) {
        return javaTypes.memberType(owner, method).getParameterTypes().stream()
                .map(this::render)
                .collect(Collectors.joining(", ", "(", ")"));
    }

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
