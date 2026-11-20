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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationView;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JEnumConstant;
import io.micronaut.graphql.tools.binding.internal.model.JMethod;
import io.micronaut.graphql.tools.binding.internal.model.JParameter;
import io.micronaut.graphql.tools.binding.internal.model.JProperty;
import io.micronaut.graphql.tools.binding.internal.model.Nullability;
import io.micronaut.graphql.tools.binding.internal.model.ResolverMarkers;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.WildcardElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The compile-time adapter of the type model: it reads {@link ClassElement} and {@link MethodElement}, and the annotation
 * metadata that the Micronaut visitor context builds for them. It looks only at types that the visited class reaches, its
 * supertypes and the member, parameter and return types of what it reads; it never looks a class up by name, so the
 * decision about a class depends on nothing that Gradle does not associate with it. An instance belongs to one visitor
 * start and caches per instance.
 */
final class ElementTypeModel implements TypeModel {

    private static final Pattern SYNTHETIC_NAME = Pattern.compile("arg\\d+");

    private static final String INTRODUCTION = "io.micronaut.aop.Introduction";
    private static final String INTROSPECTED = "io.micronaut.core.annotation.Introspected";

    // what makes a class a bean by its own declaration, as the startup's bean set sees it
    private static final Set<String> BEAN_DEFINING = Set.of("jakarta.inject.Singleton", "jakarta.inject.Scope",
            "io.micronaut.context.annotation.Bean", "io.micronaut.context.annotation.Prototype",
            "io.micronaut.context.annotation.Context", "io.micronaut.context.annotation.Infrastructure",
            "io.micronaut.runtime.context.scope.Refreshable", "io.micronaut.runtime.context.scope.ThreadLocal",
            "io.micronaut.runtime.http.scope.RequestScope");

    // the Object methods that are never resolver methods, by signature as the startup lists them
    private static final Set<String> OBJECT_SIGNATURES = Set.of("equals(java.lang.Object)", "hashCode()", "toString()",
            "getClass()", "notify()", "notifyAll()", "wait()", "wait(long)", "wait(long,int)");

    // the members of GroovyObject, which a Groovy class has and the startup never binds
    private static final Set<String> GROOVY_OBJECT = Set.of("getMetaClass", "setMetaClass", "getProperty", "setProperty",
            "invokeMethod");

    private final Map<String, ClassElement> reachable = new HashMap<>();
    private final Map<String, ElementClass> classes = new HashMap<>();
    private final Map<String, MethodElement> methods = new HashMap<>();

    /**
     * @param element a class element that the visitor was given
     * @return the class as the rules see it
     */
    ElementClass of(ClassElement element) {
        reachable.put(element.getName(), element);
        return classes.computeIfAbsent(element.getName(), it -> new ElementClass(element));
    }

    /**
     * @param signature a method signature as {@link JMethod#signature()} prints it
     * @return the element of a method that this model handed out, or {@code null}
     */
    @Nullable
    MethodElement elementOf(String signature) {
        return methods.get(signature);
    }

    @Override
    public JClass resolve(TypeRef ref) {
        ClassElement element = reachable.get(ref.rawName());
        if (element == null) {
            throw new IllegalArgumentException(ref.rawName() + " is not reachable from a class that was resolved here.");
        }
        return of(element);
    }

    /**
     * @param element a resolver class, or an introduction interface that is a resolver bean
     * @return whether the class is one the visitor checks: a concrete class that is a bean, or an introduction advice
     * interface with a scope annotation of its own, whose methods are all abstract and whose generated bean is concrete. A concrete class is a bean when a
     * bean-defining annotation is declared on it. The resolver annotations and the marker interfaces carry no bean
     * stereotype, so a class that only carries one, or inherits one, is not one the startup ever sees.
     */
    static boolean isBeanClass(ClassElement element) {
        if (element.isInterface()) {
            return element.hasStereotype(INTRODUCTION) && BEAN_DEFINING.stream().anyMatch(element::hasDeclaredStereotype);
        }
        if (element.isAbstract() || element.isEnum() || element.isRecord()) {
            return false;
        }
        if (BEAN_DEFINING.stream().anyMatch(element::hasDeclaredStereotype)) {
            return true;
        }
        return false;
    }

    private TypeRef ref(ClassElement type, AnnotationMetadata declaration) {
        return ref(type, declaration, Set.of());
    }

    private TypeRef ref(ClassElement type, AnnotationMetadata declaration, Set<String> variables) {
        boolean array = type.isArray();
        ClassElement raw = resolved(array ? type.fromArray() : type);
        reachable.put(raw.getName(), erased(raw));
        Set<String> open = raw instanceof GenericPlaceholderElement placeholder
                ? with(variables, placeholder.getVariableName()) : variables;
        List<TypeRef> arguments = new ArrayList<>();
        for (ClassElement argument : raw.getBoundGenericTypes()) {
            arguments.add(argument(argument, open));
        }
        Nullability nullability = declaration.hasStereotype("javax.annotation.Nullable")
                || declaration.hasStereotype("jakarta.annotation.Nullable")
                || declaration.hasStereotype("io.micronaut.core.annotation.Nullable")
                ? Nullability.NULLABLE
                : declaration.hasStereotype("io.micronaut.core.annotation.NonNull")
                || declaration.hasStereotype("jakarta.annotation.Nonnull")
                ? Nullability.NON_NULL : Nullability.UNKNOWN;
        return new TypeRef(raw.getName(), arguments, nullability, raw.isPrimitive(), array);
    }

    // a wildcard or a type variable is read as its first bound, as the runtime reads the type argument; a variable that is
    // bound by itself (T extends Comparable<T>) is followed once and read as its bound without arguments the second time
    private TypeRef argument(ClassElement argument, Set<String> variables) {
        if (argument instanceof WildcardElement wildcard) {
            List<? extends ClassElement> bounds = wildcard.getUpperBounds();
            return bounds.isEmpty() ? ref(argument, AnnotationMetadata.EMPTY_METADATA, variables)
                    : argument(bounds.get(0), variables);
        }
        if (argument instanceof GenericPlaceholderElement placeholder) {
            if (placeholder.getResolved().isPresent()) {
                return argument(placeholder.getResolved().get(), variables);
            }
            List<? extends ClassElement> bounds = placeholder.getBounds();
            if (bounds.isEmpty()) {
                return ref(argument, AnnotationMetadata.EMPTY_METADATA, variables);
            }
            if (variables.contains(placeholder.getVariableName())) {
                ClassElement bound = bounds.get(0);
                reachable.put(bound.getName(), erased(bound));
                return new TypeRef(bound.getName(), List.of(), Nullability.UNKNOWN, bound.isPrimitive(), false);
            }
            return argument(bounds.get(0), with(variables, placeholder.getVariableName()));
        }
        return ref(argument, AnnotationMetadata.EMPTY_METADATA, variables);
    }

    // a type variable that a subclass binds (Base<T> extended as Base<CompletionStage<String>>) is the type it is bound to
    private static ClassElement resolved(ClassElement type) {
        return type instanceof GenericPlaceholderElement placeholder ? placeholder.getResolved().orElse(type) : type;
    }

    // what the rules ask of a reached type is its erasure: Micronaut resolves the type arguments of a type variable that is
    // bound by itself (T extends Comparable<T>) without end when it is asked whether such a type is assignable
    private static ClassElement erased(ClassElement element) {
        ClassElement current = element;
        for (int i = 0; i < 16; i++) {
            if (current instanceof GenericPlaceholderElement placeholder && !placeholder.getBounds().isEmpty()) {
                current = placeholder.getBounds().get(0);
            } else if (current instanceof WildcardElement wildcard && !wildcard.getUpperBounds().isEmpty()) {
                current = wildcard.getUpperBounds().get(0);
            } else {
                break;
            }
        }
        // a primitive and a type without arguments are their own erasure
        return current.isPrimitive() || current.getBoundGenericTypes().isEmpty() ? current : current.getRawClassElement();
    }

    private static Set<String> with(Set<String> variables, String name) {
        Set<String> result = new LinkedHashSet<>(variables);
        result.add(name);
        return result;
    }

    private static AnnotationView view(@Nullable AnnotationMetadata metadata) {
        return metadata == null || metadata == AnnotationMetadata.EMPTY_METADATA
                ? AnnotationView.none() : new MetadataView(metadata);
    }

    // the name that Class#getName gives, which the startup prints in a method signature
    private static String typeName(ClassElement type) {
        if (!type.isArray()) {
            return type.getName();
        }
        ClassElement component = type.fromArray();
        String name = typeName(component);
        if (component.isArray()) {
            return "[" + name;
        }
        if (component.isPrimitive()) {
            return "[" + switch (name) {
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "double" -> "D";
                case "float" -> "F";
                case "int" -> "I";
                case "long" -> "J";
                default -> "S";
            };
        }
        return "[L" + name + ";";
    }

    private record MetadataView(AnnotationMetadata metadata) implements AnnotationView {

        @Override
        public boolean has(String annotation) {
            return metadata.hasStereotype(annotation);
        }

        @Override
        public Optional<String> stringValue(String annotation, String member) {
            return metadata.stringValue(annotation, member);
        }

        @Override
        public List<String> classValues(String annotation, String member) {
            List<String> names = new ArrayList<>();
            metadata.findAnnotation(annotation).ifPresent(value -> {
                for (AnnotationClassValue<?> classValue : value.annotationClassValues(member)) {
                    names.add(classValue.getName());
                }
            });
            return names;
        }
    }

    final class ElementClass implements JClass {

        private final ClassElement element;
        @Nullable
        private List<JMethod> bindable;
        @Nullable
        private ResolverMarkers markers;
        @Nullable
        private List<PropertyElement> beanProperties;

        ElementClass(ClassElement element) {
            this.element = element;
        }

        ClassElement element() {
            return element;
        }

        @Override
        public String name() {
            return element.getName();
        }

        @Override
        public Kind kind() {
            if (element.isInterface()) {
                return Kind.INTERFACE;
            }
            if (element.isEnum()) {
                return Kind.ENUM;
            }
            if (element.isRecord()) {
                return Kind.RECORD;
            }
            return element.isAbstract() ? Kind.ABSTRACT_CLASS : Kind.CLASS;
        }

        @Override
        public AnnotationView annotations() {
            return view(element.getAnnotationMetadata());
        }

        @Override
        public ResolverMarkers markers() {
            if (markers == null) {
                markers = MarkerAnalysis.of(element);
            }
            return markers;
        }

        @Override
        public List<JMethod> bindableMethods() {
            if (bindable == null) {
                List<JMethod> result = new ArrayList<>();
                ResolverMarkers fixed = markers();
                for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
                    if (isBindable(method) && fixed.admits(method.getName())) {
                        ElementMethod adapted = new ElementMethod(method);
                        methods.put(adapted.signature(), method);
                        result.add(adapted);
                    }
                }
                bindable = result;
            }
            return bindable;
        }

        private boolean isBindable(MethodElement method) {
            if (!method.isPublic() || method.isStatic() || method.isSynthetic()) {
                return false;
            }
            String name = method.getName();
            // the members that the Groovy compiler adds, which the startup never binds; a Java method may start with $
            if ((name.startsWith("$") || GROOVY_OBJECT.contains(name)) && element.isAssignable("groovy.lang.GroovyObject")) {
                return false;
            }
            if (method.getDeclaringType().getName().equals("java.lang.Object")) {
                return false;
            }
            String signature = name + Arrays.stream(method.getParameters()).map(it -> typeName(it.getType()))
                    .collect(Collectors.joining(",", "(", ")"));
            return !OBJECT_SIGNATURES.contains(signature);
        }

        // the properties as the introspection visitor of Micronaut reads them, which is what the startup finds in the
        // introspection: the same query, the properties that are not excluded, and the parameters of the primary constructor
        private List<PropertyElement> beanProperties() {
            if (beanProperties == null) {
                beanProperties = element.getBeanProperties(PropertyElementQuery.of(element)
                                .ignoreSettersWithDifferingType(true)).stream()
                        .filter(it -> !it.isExcluded())
                        .toList();
            }
            return beanProperties;
        }

        @Override
        public List<JProperty> readableProperties() {
            return beanProperties().stream().filter(it -> !it.isWriteOnly()).<JProperty>map(ElementProperty::new).toList();
        }

        @Override
        public List<JProperty> writableProperties() {
            // the parameters of the primary constructor and of the static creator: the introspection sets a property from either
            Set<String> constructorArguments = Stream.of(element.getPrimaryConstructor(), element.findStaticCreator())
                    .flatMap(Optional::stream)
                    .flatMap(it -> Arrays.stream(it.getParameters()))
                    .map(ParameterElement::getName)
                    .collect(Collectors.toSet());
            return beanProperties().stream()
                    .filter(it -> !it.isReadOnly() || constructorArguments.contains(it.getName()))
                    .<JProperty>map(ElementProperty::new)
                    .toList();
        }

        // a class has an introspection when it is annotated with @Introspected or an annotation that carries it; the
        // classes that another class lists in @Introspected(classes) are not visible from this one
        @Override
        public boolean introspected() {
            return element.hasStereotype(INTROSPECTED);
        }

        @Override
        public List<JEnumConstant> enumConstants() {
            if (!(element instanceof EnumElement enumElement)) {
                return List.of();
            }
            return enumElement.elements().stream()
                    .map(it -> new JEnumConstant(it.getName(), view(it.getAnnotationMetadata())))
                    .toList();
        }

        @Override
        public boolean isAssignableTo(String binaryName) {
            return element.getName().equals(binaryName) || element.isAssignable(binaryName);
        }

        @Override
        public List<TypeRef> typeArgumentsOf(String supertypeBinaryName) {
            return element.getTypeArguments(supertypeBinaryName).values().stream()
                    .map(it -> argument(it, Set.of()))
                    .toList();
        }

        @Override
        public List<JClass> supertypesAnnotatedWith(String annotation) {
            List<JClass> result = new ArrayList<>();
            Set<String> visited = new LinkedHashSet<>();
            collect(element, annotation, visited, result);
            return result;
        }

        private void collect(ClassElement type, String annotation, Set<String> visited, List<JClass> result) {
            List<ClassElement> parents = new ArrayList<>();
            type.getSuperType().ifPresent(parents::add);
            parents.addAll(type.getInterfaces());
            for (ClassElement parent : parents) {
                if (!visited.add(parent.getName()) || parent.getName().equals("java.lang.Object")) {
                    continue;
                }
                if (parent.hasDeclaredAnnotation(annotation)) {
                    result.add(of(parent));
                }
                collect(parent, annotation, visited, result);
            }
        }
    }

    private final class ElementMethod implements JMethod {

        private final MethodElement method;
        private final String signature;

        ElementMethod(MethodElement method) {
            this.method = method;
            this.signature = method.getDeclaringType().getName() + "#" + method.getName()
                    + Arrays.stream(method.getParameters()).map(it -> typeName(it.getType()))
                    .collect(Collectors.joining(",", "(", ")"));
        }

        @Override
        public String name() {
            return method.getName();
        }

        @Override
        public JClass declaringType() {
            return of(method.getDeclaringType());
        }

        @Override
        public List<JParameter> parameters() {
            return Arrays.stream(method.getParameters()).<JParameter>map(ElementParameter::new).toList();
        }

        @Override
        public TypeRef genericReturnType() {
            return ref(method.getGenericReturnType(), method.getMethodAnnotationMetadata());
        }

        @Override
        public boolean suspend() {
            return method.isSuspend();
        }

        @Override
        public AnnotationView annotations() {
            return view(method.getAnnotationMetadata());
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private final class ElementParameter implements JParameter {

        private final ParameterElement parameter;

        ElementParameter(ParameterElement parameter) {
            this.parameter = parameter;
        }

        @Override
        public String name() {
            return parameter.getName();
        }

        @Override
        public boolean nameIsSynthetic() {
            return SYNTHETIC_NAME.matcher(parameter.getName()).matches();
        }

        @Override
        public TypeRef type() {
            return ref(parameter.getGenericType(), parameter.getAnnotationMetadata());
        }

        @Override
        public AnnotationView annotations() {
            return view(parameter.getAnnotationMetadata());
        }
    }

    private final class ElementProperty implements JProperty {

        private final PropertyElement property;

        ElementProperty(PropertyElement property) {
            this.property = property;
        }

        @Override
        public String name() {
            return property.getName();
        }

        @Override
        public TypeRef type() {
            return ref(property.getGenericType(), property.getAnnotationMetadata());
        }

        @Override
        public AnnotationView annotations() {
            return view(property.getAnnotationMetadata());
        }

        @Override
        public String accessorText() {
            return "get" + Character.toUpperCase(property.getName().charAt(0)) + property.getName().substring(1) + "()";
        }
    }
}
