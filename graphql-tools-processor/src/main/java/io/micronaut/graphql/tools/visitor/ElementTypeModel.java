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
import io.micronaut.graphql.tools.binding.internal.model.AnnotationView;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JEnumConstant;
import io.micronaut.graphql.tools.binding.internal.model.JMethod;
import io.micronaut.graphql.tools.binding.internal.model.JParameter;
import io.micronaut.graphql.tools.binding.internal.model.JProperty;
import io.micronaut.graphql.tools.binding.internal.model.Nullability;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
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
     * @return whether the class is one the visitor checks: it is concrete, or an introduction advice interface, whose methods
     * are all abstract and whose generated bean is concrete
     */
    static boolean isBeanClass(ClassElement element) {
        if (element.isInterface()) {
            return element.hasStereotype(INTRODUCTION);
        }
        return !element.isAbstract() && !element.isEnum() && !element.isRecord();
    }

    private TypeRef ref(ClassElement type, AnnotationMetadata declaration) {
        boolean array = type.isArray();
        ClassElement raw = array ? type.fromArray() : type;
        reachable.put(raw.getName(), raw);
        List<TypeRef> arguments = new ArrayList<>();
        for (ClassElement argument : raw.getBoundGenericTypes()) {
            ClassElement resolved = bound(argument);
            arguments.add(ref(resolved, AnnotationMetadata.EMPTY_METADATA));
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

    // a wildcard or a type variable is read as its first bound, as the runtime reads the type argument
    private static ClassElement bound(ClassElement argument) {
        if (argument instanceof WildcardElement wildcard) {
            List<? extends ClassElement> bounds = wildcard.getUpperBounds();
            return bounds.isEmpty() ? argument : bound(bounds.get(0));
        }
        if (argument instanceof GenericPlaceholderElement placeholder) {
            List<? extends ClassElement> bounds = placeholder.getBounds();
            return bounds.isEmpty() ? argument : bound(bounds.get(0));
        }
        return argument;
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
        public List<JMethod> bindableMethods() {
            if (bindable == null) {
                List<JMethod> result = new ArrayList<>();
                for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
                    if (isBindable(method)) {
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
            if (name.startsWith("$") || GROOVY_OBJECT.contains(name) && element.isAssignable("groovy.lang.GroovyObject")) {
                return false;
            }
            if (method.getDeclaringType().getName().equals("java.lang.Object")) {
                return false;
            }
            String signature = name + Arrays.stream(method.getParameters()).map(it -> typeName(it.getType()))
                    .collect(Collectors.joining(",", "(", ")"));
            return !OBJECT_SIGNATURES.contains(signature);
        }

        @Override
        public List<JProperty> readableProperties() {
            throw new UnsupportedOperationException("The properties of a class are read by the input and enum checks");
        }

        @Override
        public List<JProperty> writableProperties() {
            throw new UnsupportedOperationException("The properties of a class are read by the input and enum checks");
        }

        @Override
        public boolean introspected() {
            throw new UnsupportedOperationException("The introspection of a class is read by the input and enum checks");
        }

        @Override
        public List<JEnumConstant> enumConstants() {
            throw new UnsupportedOperationException("The constants of an enum are read by the input and enum checks");
        }

        @Override
        public boolean isAssignableTo(String binaryName) {
            return element.getName().equals(binaryName) || element.isAssignable(binaryName);
        }

        @Override
        public List<TypeRef> typeArgumentsOf(String supertypeBinaryName) {
            return element.getTypeArguments(supertypeBinaryName).values().stream()
                    .map(it -> ref(bound(it), AnnotationMetadata.EMPTY_METADATA))
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
}
