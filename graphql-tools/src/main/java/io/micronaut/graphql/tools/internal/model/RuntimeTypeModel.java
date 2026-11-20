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
package io.micronaut.graphql.tools.internal.model;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
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
import io.micronaut.graphql.tools.internal.MicronautUtils;
import io.micronaut.graphql.tools.internal.checks.StrictModeChecks;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The runtime adapter of the type model: it reads {@link BeanDefinition}, {@link ExecutableMethod}, {@link Argument} and
 * {@link BeanIntrospection}, and the annotation metadata that the Micronaut processors compiled into them. A class
 * without that metadata has no annotations here, which is what the runtime rules have always seen.
 */
@Internal
public final class RuntimeTypeModel implements TypeModel {

    private static final Pattern SYNTHETIC_NAME = Pattern.compile("arg\\d+");

    private final Map<Class<?>, JClass> classes = new HashMap<>();
    private final Map<String, Class<?>> reachable = new HashMap<>();

    /**
     * @param type a class
     * @return the class as the rules see it; an enum or input class reads its introspection, a bean also reads its
     * executable methods through {@link #of(BeanDefinition)}
     */
    public JClass of(Class<?> type) {
        reachable.put(type.getName(), type);
        return classes.computeIfAbsent(type, it -> new RuntimeClass(it, null, null));
    }

    /**
     * @param beanDefinition a resolver bean
     * @return the class of the bean, with its executable methods and its annotation metadata
     */
    public JClass of(BeanDefinition<?> beanDefinition) {
        Class<?> type = GraphQLResolversRegistry.getUserBeanType(beanDefinition);
        reachable.put(type.getName(), type);
        return classes.computeIfAbsent(type, it -> new RuntimeClass(it, beanDefinition, null));
    }

    /**
     * @param introspection the introspection of a class
     * @return the class
     */
    public JClass of(BeanIntrospection<?> introspection) {
        Class<?> type = introspection.getBeanType();
        reachable.put(type.getName(), type);
        return classes.computeIfAbsent(type, it -> new RuntimeClass(it, null, introspection));
    }

    @Override
    public JClass resolve(TypeRef ref) {
        Class<?> type = reachable.get(ref.rawName());
        if (type == null) {
            throw new IllegalArgumentException(ref.rawName() + " is not reachable from a class that was resolved here.");
        }
        return of(type);
    }

    /**
     * @param method an executable method of a resolver bean
     * @return the method as the rules see it
     */
    JMethod method(ExecutableMethod<?, ?> method) {
        return new RuntimeMethod(method);
    }

    /**
     * @param arguments the arguments of an executable
     * @return its parameters, in declaration order
     */
    public List<JParameter> parameters(Argument<?>[] arguments) {
        List<JParameter> result = new ArrayList<>(arguments.length);
        for (Argument<?> argument : arguments) {
            result.add(new RuntimeParameter(argument));
        }
        return result;
    }

    public TypeRef ref(Argument<?> argument) {
        Class<?> type = argument.getType();
        boolean array = type.isArray();
        Class<?> raw = array ? type.getComponentType() : type;
        reachable.put(raw.getName(), raw);
        List<TypeRef> typeArguments = new ArrayList<>();
        for (Argument<?> typeParameter : argument.getTypeParameters()) {
            typeArguments.add(ref(typeParameter));
        }
        Nullability nullability = argument.isNullable()
                ? Nullability.NULLABLE
                : argument.isNonNull() ? Nullability.NON_NULL : Nullability.UNKNOWN;
        return new TypeRef(raw.getName(), typeArguments, nullability, raw.isPrimitive(), array);
    }

    private static AnnotationView view(@Nullable AnnotationMetadata metadata) {
        return metadata == null || metadata == AnnotationMetadata.EMPTY_METADATA
                ? AnnotationView.none()
                : new MetadataView(metadata);
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
            return Arrays.stream(metadata.classValues(annotation, member)).map(Class::getName).toList();
        }
    }

    /**
     * Reads the annotations of an element on first use: finding the introspection of an enum costs a lookup that the
     * rules need only when a constant has no schema value.
     */
    private static final class LazyView implements AnnotationView {

        private final Supplier<AnnotationView> source;
        @Nullable
        private AnnotationView resolved;

        LazyView(Supplier<AnnotationView> source) {
            this.source = source;
        }

        private AnnotationView resolved() {
            if (resolved == null) {
                AnnotationView view = source.get();
                resolved = view == null ? AnnotationView.none() : view;
            }
            return resolved;
        }

        @Override
        public boolean has(String annotation) {
            return resolved().has(annotation);
        }

        @Override
        public Optional<String> stringValue(String annotation, String member) {
            return resolved().stringValue(annotation, member);
        }

        @Override
        public List<String> classValues(String annotation, String member) {
            return resolved().classValues(annotation, member);
        }
    }

    private final class RuntimeClass implements JClass {

        private final Class<?> type;
        @Nullable
        private final BeanDefinition<?> beanDefinition;
        @Nullable
        private BeanIntrospection<?> introspection;
        private boolean introspectionLooked;

        @Nullable
        private Set<String> supertypeNames;

        RuntimeClass(Class<?> type, @Nullable BeanDefinition<?> beanDefinition,
                     @Nullable BeanIntrospection<?> introspection) {
            this.type = type;
            this.beanDefinition = beanDefinition;
            this.introspection = introspection;
            this.introspectionLooked = introspection != null;
        }

        @Nullable
        private BeanIntrospection<?> introspection() {
            if (!introspectionLooked) {
                introspectionLooked = true;
                introspection = BeanIntrospector.SHARED.findIntrospection((Class<Object>) type).orElse(null);
            }
            return introspection;
        }

        @Override
        public String name() {
            return type.getName();
        }

        @Override
        public Kind kind() {
            if (type.isEnum()) {
                return Kind.ENUM;
            }
            if (type.isInterface()) {
                return Kind.INTERFACE;
            }
            if (type.isRecord()) {
                return Kind.RECORD;
            }
            return Modifier.isAbstract(type.getModifiers()) ? Kind.ABSTRACT_CLASS : Kind.CLASS;
        }

        @Override
        public AnnotationView annotations() {
            if (beanDefinition != null) {
                return view(beanDefinition.getAnnotationMetadata());
            }
            BeanIntrospection<?> own = introspection();
            return own == null ? AnnotationView.none() : view(own.getAnnotationMetadata());
        }

        @Override
        public ResolverMarkers markers() {
            return beanDefinition == null ? ResolverMarkers.NONE : GraphQLResolversRegistry.readMarkers(beanDefinition);
        }

        @Override
        public List<JMethod> bindableMethods() {
            if (beanDefinition == null) {
                return List.of();
            }
            List<JMethod> result = new ArrayList<>();
            ResolverMarkers fixed = markers();
            for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
                boolean objectMethod = method.getDeclaringType() == Object.class
                        || StrictModeChecks.isObjectSignature(method.getMethodName(), method.getArgumentTypes());
                if (!objectMethod && fixed.admits(method.getMethodName())) {
                    result.add(new RuntimeMethod(method));
                }
            }
            return result;
        }

        @Override
        public List<JProperty> readableProperties() {
            BeanIntrospection<?> own = introspection();
            if (own == null) {
                return List.of();
            }
            return own.getBeanProperties().stream()
                    .filter(it -> !it.isWriteOnly())
                    .<JProperty>map(RuntimeProperty::new)
                    .toList();
        }

        @Override
        public List<JProperty> writableProperties() {
            BeanIntrospection<?> own = introspection();
            if (own == null) {
                return List.of();
            }
            Set<String> constructorArguments = Arrays.stream(own.getConstructorArguments())
                    .map(Argument::getName)
                    .collect(Collectors.toSet());
            return own.getBeanProperties().stream()
                    .filter(it -> !it.isReadOnly() || constructorArguments.contains(it.getName()))
                    .<JProperty>map(RuntimeProperty::new)
                    .toList();
        }

        @Override
        public boolean introspected() {
            return introspection() != null;
        }

        @Override
        public List<JEnumConstant> enumConstants() {
            if (!(introspection() instanceof EnumBeanIntrospection<?> enumIntrospection)) {
                return List.of();
            }
            List<JEnumConstant> result = new ArrayList<>();
            enumIntrospection.getConstants().forEach(it -> {
                String name = ((Enum<?>) it.getValue()).name();
                result.add(new JEnumConstant(name, new LazyView(() -> view(it.getAnnotationMetadata()))));
            });
            return result;
        }

        @Override
        public boolean isAssignableTo(String binaryName) {
            if (supertypeNames == null) {
                supertypeNames = supertypes(true).stream().map(Class::getName).collect(Collectors.toSet());
            }
            return supertypeNames.contains(binaryName);
        }

        @Override
        public List<TypeRef> typeArgumentsOf(String supertypeBinaryName) {
            for (Class<?> supertype : supertypes(false)) {
                if (supertype.getName().equals(supertypeBinaryName)) {
                    Class<?>[] arguments = supertype.isInterface()
                            ? GenericTypeUtils.resolveInterfaceTypeArguments(type, supertype)
                            : GenericTypeUtils.resolveSuperTypeGenericArguments(type, supertype);
                    return Arrays.stream(arguments).map(it -> {
                        reachable.put(it.getName(), it);
                        return TypeRef.of(it.getName());
                    }).toList();
                }
            }
            return List.of();
        }

        @Override
        public List<JClass> supertypesAnnotatedWith(String annotation) {
            return supertypes(false).stream()
                    .map(RuntimeTypeModel.this::of)
                    .filter(it -> it.annotations().has(annotation))
                    .toList();
        }

        private Collection<Class<?>> supertypes(boolean includeSelf) {
            Set<Class<?>> result = new LinkedHashSet<>();
            Deque<Class<?>> pending = new ArrayDeque<>(List.of(type));
            Set<Class<?>> visited = new HashSet<>();
            while (!pending.isEmpty()) {
                Class<?> next = pending.poll();
                if (!visited.add(next)) {
                    continue;
                }
                if (includeSelf || next != type) {
                    result.add(next);
                }
                if (next.getSuperclass() != null) {
                    pending.add(next.getSuperclass());
                }
                pending.addAll(Arrays.asList(next.getInterfaces()));
            }
            return result;
        }
    }

    private final class RuntimeMethod implements JMethod {

        private final ExecutableMethod<?, ?> method;

        RuntimeMethod(ExecutableMethod<?, ?> method) {
            this.method = method;
        }

        @Override
        public String name() {
            return method.getMethodName();
        }

        @Override
        public JClass declaringType() {
            return of(method.getDeclaringType());
        }

        @Override
        public List<JParameter> parameters() {
            return Arrays.stream(declaredArguments()).<JParameter>map(RuntimeParameter::new).toList();
        }

        // the continuation of a suspend method is not a parameter of the resolver
        private Argument<?>[] declaredArguments() {
            Argument<?>[] arguments = method.getArguments();
            return method.isSuspend() ? Arrays.copyOf(arguments, arguments.length - 1) : arguments;
        }

        @Override
        public TypeRef genericReturnType() {
            return ref(method.getReturnType().asArgument());
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
            return method.getDeclaringType().getName() + "#" + method.getMethodName()
                    + Arrays.stream(declaredArguments()).map(it -> it.getType().getName())
                    .collect(Collectors.joining(",", "(", ")"));
        }
    }

    private final class RuntimeParameter implements JParameter {

        private final Argument<?> argument;

        RuntimeParameter(Argument<?> argument) {
            this.argument = argument;
        }

        @Override
        public String name() {
            return argument.getName();
        }

        @Override
        public boolean nameIsSynthetic() {
            return SYNTHETIC_NAME.matcher(argument.getName()).matches();
        }

        @Override
        public TypeRef type() {
            return ref(argument);
        }

        @Override
        public AnnotationView annotations() {
            return view(argument.getAnnotationMetadata());
        }
    }

    private final class RuntimeProperty implements JProperty {

        private final BeanProperty<?, ?> property;

        RuntimeProperty(BeanProperty<?, ?> property) {
            this.property = property;
        }

        @Override
        public String name() {
            return property.getName();
        }

        @Override
        public TypeRef type() {
            return ref(property.asArgument());
        }

        @Override
        public AnnotationView annotations() {
            return view(property.getAnnotationMetadata());
        }

        @Override
        public String accessorText() {
            return MicronautUtils.getPropertyMethodName(property);
        }
    }
}
