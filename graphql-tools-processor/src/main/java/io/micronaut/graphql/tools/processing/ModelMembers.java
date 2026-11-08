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

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The members of a Java type that Micronaut's bean introspection, which the library reads at runtime, gives it, and
 * which Java type is introspected for a type the schema maps to a Java type:
 * <ul>
 *     <li>properties: public getters {@code getX()} and, for {@code boolean} and {@code Boolean}, {@code isX()}, record
 *     components, and public setters {@code setX(value)} (write-only properties), inherited ones included;</li>
 *     <li>methods annotated {@code @GraphQLField}.</li>
 * </ul>
 */
final class ModelMembers {

    private final JavaTypes javaTypes;
    private final Map<String, List<TypeElement>> implementations = new LinkedHashMap<>();

    /**
     * @param javaTypes the type helpers
     * @param types     the types of the compilation, where implementations of contracts are looked up
     */
    ModelMembers(JavaTypes javaTypes, Collection<TypeElement> types) {
        this.javaTypes = javaTypes;
        for (TypeElement type : types) {
            if (type.getKind() == ElementKind.CLASS) {
                contractOf(type).ifPresent(contract ->
                        implementations.computeIfAbsent(javaTypes.name(contract), it -> new ArrayList<>()).add(type));
            }
        }
    }

    /**
     * @param type a class
     * @return the interface its {@code @GraphQLType(TheInterface.class)} names, if any
     */
    Optional<TypeElement> contractOf(TypeElement type) {
        return Annotations.find(type, Annotations.TYPE)
                .flatMap(Annotations::classValue)
                .filter(it -> it.getKind() == ElementKind.INTERFACE);
    }

    /**
     * @param contract an interface
     * @return the classes of the compilation annotated {@code @GraphQLType(contract)}
     */
    List<TypeElement> implementations(TypeElement contract) {
        return implementations.getOrDefault(javaTypes.name(contract), List.of());
    }

    /**
     * @param type a Java type
     * @return whether it has a bean introspection: {@code @GraphQLType}, {@code @GraphQLInput} or {@code @Introspected}
     * (also as a meta-annotation)
     */
    boolean isIntrospected(TypeElement type) {
        return Annotations.has(type, Annotations.TYPE) || Annotations.has(type, Annotations.INPUT)
                || Annotations.hasStereotype(type, java.util.Set.of(Annotations.INTROSPECTED));
    }

    /**
     * The Java type whose introspection the library reads for a Java type that a field returns, as
     * {@code GraphQLBeanIntrospectionRegistry} decides at startup: the contract interface of the class (or the interface
     * itself) if it has its own introspection; for an interface without one, its single implementation; otherwise the
     * class itself.
     *
     * @param type the Java type a field returns
     * @return the introspected type, or empty if it cannot be decided here (an interface whose implementations are not
     * all in this compilation, or that has none or several)
     */
    Optional<TypeElement> introspectedType(TypeElement type) {
        TypeElement contract = type.getKind() == ElementKind.INTERFACE ? type : contractOf(type).orElse(null);
        if (contract != null && isIntrospected(contract)) {
            return Optional.of(contract);
        }
        if (type.getKind() == ElementKind.INTERFACE) {
            List<TypeElement> candidates = implementations(type);
            return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
        }
        return Optional.of(type);
    }

    /**
     * @param type an introspected type
     * @return the type the library looks type resolvers up for: its contract interface for a class annotated
     * {@code @GraphQLType(TheInterface.class)}, otherwise the type itself
     */
    TypeElement sourceClass(TypeElement type) {
        return contractOf(type).orElse(type);
    }

    /**
     * @param type an introspected type
     * @param name a property name
     * @return the property, if the introspection has it
     */
    Optional<Property> property(TypeElement type, String name) {
        ExecutableElement getter = null;
        ExecutableElement setter = null;
        if (type.getKind() == ElementKind.RECORD) {
            for (RecordComponentElement component : type.getRecordComponents()) {
                if (component.getSimpleName().contentEquals(name)) {
                    return Optional.of(new Property(name, component.getAccessor(), component.getAccessor().getReturnType()));
                }
            }
        }
        for (ExecutableElement method : methods(type)) {
            String methodName = method.getSimpleName().toString();
            if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID) {
                if (methodName.startsWith("get") && methodName.length() > 3 && name.equals(decapitalize(methodName.substring(3)))
                        || methodName.startsWith("is") && methodName.length() > 2 && isBoolean(method.getReturnType())
                        && name.equals(decapitalize(methodName.substring(2)))) {
                    getter = getter == null ? method : getter;
                }
            } else if (method.getParameters().size() == 1 && methodName.startsWith("set") && methodName.length() > 3
                    && name.equals(decapitalize(methodName.substring(3)))) {
                setter = setter == null ? method : setter;
            }
        }
        if (getter != null) {
            return Optional.of(new Property(name, getter, getter.getReturnType()));
        }
        if (setter != null) {
            return Optional.of(new Property(name, setter, setter.getParameters().get(0).asType()));
        }
        return Optional.empty();
    }

    /**
     * @param type an introspected type
     * @param name a field name
     * @return its methods annotated {@code @GraphQLField} of that name
     */
    List<ExecutableElement> fieldMethods(TypeElement type, String name) {
        return methods(type).stream()
                .filter(it -> it.getSimpleName().contentEquals(name))
                .filter(it -> Annotations.has(it, Annotations.FIELD))
                .toList();
    }

    private List<ExecutableElement> methods(TypeElement type) {
        List<ExecutableElement> result = new ArrayList<>();
        for (ExecutableElement method : ElementFilter.methodsIn(javaTypes.elements().getAllMembers(type))) {
            TypeElement declaring = (TypeElement) method.getEnclosingElement();
            boolean visible = type.getKind() == ElementKind.INTERFACE || method.getModifiers().contains(Modifier.PUBLIC);
            if (visible && !method.getModifiers().contains(Modifier.STATIC)
                    && !declaring.getQualifiedName().contentEquals("java.lang.Object")) {
                result.add(method);
            }
        }
        return result;
    }

    private static boolean isBoolean(TypeMirror type) {
        return type.getKind() == TypeKind.BOOLEAN || type.toString().equals("java.lang.Boolean");
    }

    /**
     * @param name a property name after {@code get}, {@code is} or {@code set}
     * @return the name as a bean property: the first character in lower case, unless the first two are upper case
     * ({@code URL} stays {@code URL})
     */
    static String decapitalize(String name) {
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * A property of an introspected type.
     *
     * @param name     its name
     * @param accessor its getter, record accessor, or (write-only) setter
     * @param type     its type
     */
    record Property(String name, ExecutableElement accessor, TypeMirror type) {
    }

}
