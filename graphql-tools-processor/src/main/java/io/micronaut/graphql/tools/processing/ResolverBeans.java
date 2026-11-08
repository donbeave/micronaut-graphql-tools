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

/**
 * The root and type resolver beans of the compilation and their methods, found the way the library registers them at
 * startup:
 * <ul>
 *     <li>a bean is a concrete class with a bean-defining annotation ({@code @Singleton}, ..., or the resolver
 *     annotation itself, which is one), or a concrete class that inherits the resolver annotation from a superclass;
 *     it is a root or type resolver when it or one of its supertypes, classes or interfaces, carries
 *     {@code @GraphQLRootResolver} or {@code @GraphQLTypeResolver};</li>
 *     <li>its executable methods are its public instance methods, inherited ones included, without those of
 *     {@code Object};</li>
 *     <li>a method has a schema coordinate when it, or a method it overrides, carries {@code @GraphQLSchemaCoordinate};
 *     on a bean where any method has one, only those methods bind;</li>
 *     <li>a type resolver resolves the class named by each {@code @GraphQLTypeResolver} of its class hierarchy; with
 *     several such classes, each method resolves the class of its first parameter.</li>
 * </ul>
 */
final class ResolverBeans {

    private static final Set<String> OBJECT_METHODS = Set.of("toString()", "hashCode()", "equals(java.lang.Object)",
            "clone()", "finalize()");

    private final JavaTypes javaTypes;
    private final List<Bean> beans = new ArrayList<>();

    /**
     * @param javaTypes the type helpers
     * @param types     the types of the compilation
     */
    ResolverBeans(JavaTypes javaTypes, Collection<TypeElement> types) {
        this.javaTypes = javaTypes;
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

    /**
     * @return every resolver bean
     */
    List<Bean> beans() {
        return beans;
    }

    /**
     * @return the root resolver beans
     */
    List<Bean> rootBeans() {
        return beans.stream().filter(Bean::root).toList();
    }

    /**
     * @param modelClass a Java type
     * @return the type resolver beans with methods for it
     */
    List<Bean> typeBeans(TypeElement modelClass) {
        return beans.stream().filter(it -> it.methodsByModelClass().containsKey(javaTypes.name(modelClass))).toList();
    }

    /**
     * @param type a class of the compilation
     * @return whether Micronaut makes it a bean
     */
    boolean isBean(TypeElement type) {
        if (type.getKind() != ElementKind.CLASS || type.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        if (Annotations.hasStereotype(type, Annotations.BEAN_DEFINING)) {
            return true;
        }
        // @GraphQLRootResolver and @GraphQLTypeResolver are @Inherited: a subclass of an annotated class carries them
        return javaTypes.superclasses(type).stream().anyMatch(it -> Annotations.has(it, Annotations.ROOT_RESOLVER)
                || Annotations.has(it, Annotations.TYPE_RESOLVER));
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
                    || method.getModifiers().contains(Modifier.ABSTRACT)) {
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
                List<? extends TypeMirror> parameters = method.element().getParameters().stream()
                        .map(it -> it.asType()).toList();
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
                unassigned);
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

    /**
     * A root or type resolver bean.
     *
     * @param type                its class
     * @param name                the binary name of its class
     * @param root                whether it is a root resolver
     * @param contracts           the binary names of the types of its hierarchy that carry {@code @GraphQLTypeResolver},
     *                            and the class each names
     * @param methods             its executable methods
     * @param usesCoordinates     whether one of its methods has a schema coordinate
     * @param methodsByModelClass for a type resolver, its methods by the binary name of the class they resolve
     * @param unassigned          for a type resolver of several classes, the methods whose first parameter is none of
     *                            them
     */
    record Bean(TypeElement type,
                String name,
                boolean root,
                Map<String, TypeElement> contracts,
                List<Method> methods,
                boolean usesCoordinates,
                Map<String, List<Method>> methodsByModelClass,
                List<Method> unassigned) {

        /**
         * @param method a method of the bean
         * @return whether it can bind a field: on a bean that uses schema coordinates only the methods with one can
         */
        boolean isCandidate(Method method) {
            return !usesCoordinates || method.coordinate() != null;
        }

        /**
         * @param methods    methods of the bean
         * @param name       a field name
         * @param coordinate the coordinate of the field
         * @return the methods that can bind the field
         */
        List<Method> find(List<Method> methods, String name, String coordinate) {
            return methods.stream()
                    .filter(it -> it.element().getSimpleName().contentEquals(name))
                    .filter(it -> !usesCoordinates || coordinate.equals(it.coordinate()))
                    .toList();
        }

    }

    /**
     * An executable method of a resolver bean.
     */
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

        /**
         * @return the method
         */
        ExecutableElement element() {
            return element;
        }

        /**
         * @return its schema coordinate, or {@code null}
         */
        String coordinate() {
            return coordinate;
        }

        /**
         * @return whether it overrides a method of {@code Object}
         */
        boolean objectMethod() {
            return objectMethod;
        }

        /**
         * @return its bean
         */
        Bean bean() {
            return bean;
        }

    }

}
