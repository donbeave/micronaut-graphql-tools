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

import io.micronaut.graphql.tools.binding.internal.rules.FieldNames;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ResolverBeans {

    private static final Set<String> OBJECT_METHODS = Set.of("toString()", "hashCode()", "equals(java.lang.Object)",
            "clone()", "finalize()");

    private final JavaTypes javaTypes;
    private final boolean accessorPrefixes;
    private final List<Bean> beans = new ArrayList<>();

    ResolverBeans(JavaTypes javaTypes, Collection<TypeElement> types, boolean accessorPrefixes) {
        this.javaTypes = javaTypes;
        this.accessorPrefixes = accessorPrefixes;
        for (TypeElement type : types) {
            if (isBean(type)) {
                Bean bean = createBean(type);
                if (bean != null) {
                    beans.add(bean);
                }
            }
        }
        beans.sort(Comparator.comparing(it -> it.name()));
    }

    List<Bean> beans() {
        return beans;
    }

    List<Bean> rootBeans() {
        return beans.stream().filter(Bean::root).toList();
    }

    List<Bean> typeBeans(TypeElement modelClass) {
        return beans.stream().filter(it -> it.methodsByModelClass().containsKey(javaTypes.name(modelClass))).toList();
    }

    boolean isBean(TypeElement type) {
        if (isIntroduction(type)) {
            return true;
        }
        if (type.getKind() != ElementKind.CLASS || type.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        if (Annotations.hasStereotype(type, Annotations.BEAN_DEFINING)) {
            return true;
        }
        return javaTypes.superclasses(type).stream().anyMatch(it -> Annotations.has(it, Annotations.ROOT_RESOLVER)
                || Annotations.has(it, Annotations.TYPE_RESOLVER));
    }

    private static boolean isIntroduction(TypeElement type) {
        return type.getKind() == ElementKind.INTERFACE && Annotations.hasStereotype(type, Annotations.INTRODUCTION);
    }

    private Bean createBean(TypeElement type) {
        List<TypeElement> hierarchy = javaTypes.hierarchy(type);
        boolean root = hierarchy.stream().anyMatch(it -> Annotations.has(it, Annotations.ROOT_RESOLVER));
        Map<String, TypeElement> contracts = new LinkedHashMap<>();
        for (TypeElement element : hierarchy) {
            Annotations.find(element, Annotations.TYPE_RESOLVER)
                    .flatMap(Annotations::classValue)
                    .ifPresent(modelClass -> contracts.put(javaTypes.name(element), modelClass));
        }
        if (!root && contracts.isEmpty()) {
            return null;
        }

        List<Method> methods = new ArrayList<>();
        for (ExecutableElement method : ElementFilter.methodsIn(javaTypes.elements().getAllMembers(type))) {
            if (!method.getModifiers().contains(Modifier.PUBLIC) || method.getModifiers().contains(Modifier.STATIC)
                    || method.getModifiers().contains(Modifier.ABSTRACT) && !isIntroduction(type)) {
                continue;
            }
            TypeElement declaring = (TypeElement) method.getEnclosingElement();
            if (declaring.getQualifiedName().contentEquals("java.lang.Object")) {
                continue;
            }
            methods.add(new Method(method, coordinate(type, method).orElse(null),
                    OBJECT_METHODS.contains(javaTypes.signature(method))));
        }
        methods.sort(Comparator.comparing(it -> javaTypes.signature(it.element())));
        boolean usesCoordinates = methods.stream().anyMatch(it -> it.coordinate() != null);

        Map<String, List<Method>> byModelClass = new LinkedHashMap<>();
        List<Method> unassigned = new ArrayList<>();
        Set<String> modelClasses = new LinkedHashSet<>();
        contracts.values().forEach(it -> modelClasses.add(javaTypes.name(it)));
        for (Method method : methods) {
            if (modelClasses.isEmpty()) {
                break;
            }
            String modelClass = modelClasses.iterator().next();
            if (modelClasses.size() > 1) {
                List<? extends TypeMirror> parameters =
                        javaTypes.memberType(type, method.element()).getParameterTypes();
                String first = parameters.isEmpty() ? null : javaTypes.erasedName(parameters.get(0));
                if (first == null || !modelClasses.contains(first)) {
                    unassigned.add(method);
                    continue;
                }
                modelClass = first;
            }
            byModelClass.computeIfAbsent(modelClass, it -> new ArrayList<>()).add(method);
        }

        Bean bean = new Bean(type, javaTypes.name(type), root, contracts, methods, usesCoordinates, byModelClass,
                unassigned, accessorPrefixes);
        methods.forEach(it -> it.bean = bean);
        return bean;
    }

    private Optional<String> coordinate(TypeElement bean, ExecutableElement method) {
        Optional<String> own = Annotations.find(method, Annotations.SCHEMA_COORDINATE).flatMap(Annotations::stringValue);
        if (own.isPresent()) {
            return own;
        }
        for (TypeElement supertype : javaTypes.hierarchy(bean)) {
            for (ExecutableElement candidate : ElementFilter.methodsIn(supertype.getEnclosedElements())) {
                if (candidate.equals(method) || !candidate.getSimpleName().equals(method.getSimpleName())) {
                    continue;
                }
                Optional<AnnotationMirror> annotation = Annotations.find(candidate, Annotations.SCHEMA_COORDINATE);
                if (annotation.isPresent() && javaTypes.elements().overrides(method, candidate, bean)) {
                    return annotation.flatMap(Annotations::stringValue);
                }
            }
        }
        return Optional.empty();
    }

    record Bean(TypeElement type,
                String name,
                boolean root,
                Map<String, TypeElement> contracts,
                List<Method> methods,
                boolean usesCoordinates,
                Map<String, List<Method>> methodsByModelClass,
                List<Method> unassigned,
                boolean accessorPrefixes) {

        boolean isCandidate(Method method) {
            return !usesCoordinates || method.coordinate() != null;
        }

        List<Method> find(List<Method> methods, String name, String coordinate) {
            return methods.stream()
                    .filter(it -> usesCoordinates ? coordinate.equals(it.coordinate()) : names(it).contains(name))
                    .toList();
        }

        // whether the method may bind the field, for a rule that does not know the type of the field yet
        boolean mayBind(Method method, String fieldName, boolean declaredCoordinate) {
            if (method.coordinate() != null) {
                return method.coordinate().substring(method.coordinate().indexOf('.') + 1).equals(fieldName);
            }
            return !declaredCoordinate && names(method).contains(fieldName);
        }

        private List<String> names(Method method) {
            return FieldNames.ofResolverMethod(method.element().getSimpleName().toString(), accessorPrefixes);
        }

    }

    static final class Method {

        private final ExecutableElement element;
        private final String coordinate;
        private final boolean objectMethod;
        private Bean bean;

        Method(ExecutableElement element, String coordinate, boolean objectMethod) {
            this.element = element;
            this.coordinate = coordinate;
            this.objectMethod = objectMethod;
        }

        ExecutableElement element() {
            return element;
        }

        String coordinate() {
            return coordinate;
        }

        boolean objectMethod() {
            return objectMethod;
        }

        Bean bean() {
            return bean;
        }

    }

}
