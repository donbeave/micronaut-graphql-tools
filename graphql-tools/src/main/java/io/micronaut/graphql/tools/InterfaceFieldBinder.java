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
package io.micronaut.graphql.tools;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration.Precedence;
import io.micronaut.graphql.tools.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.exceptions.ResolverRequiredException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static io.micronaut.graphql.tools.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.MicronautUtils.toMap;

/**
 * The phase that binds the fields which an object type shares with the interfaces it implements: a field that no member of
 * the Java type and no resolver of the type can bind may be bound by a resolver of the interface, and the precedence
 * decides when both can. It works on its own lists, takes what the type phase collected, and again whatever binding a field
 * makes the type phase reach, and returns what it bound as one immutable result.
 */
@Internal
final class InterfaceFieldBinder {

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final Map<String, Class<?>> processedTypes;
    private final Precedence precedence;
    private final Callbacks callbacks;
    private final AmbiguityLog ambiguityLog;

    private final List<InterfaceField> unboundInterfaceFields = new ArrayList<>();
    private final List<InterfaceField> interfaceResolverFields = new ArrayList<>();
    private final List<InterfaceField> ownInterfaceFields = new ArrayList<>();
    private final List<InterfaceField> deferredModelMemberFields = new ArrayList<>();
    private final Map<String, String> fieldBindings = new HashMap<>();
    private final Map<String, Set<String>> resolverBindings = new HashMap<>();
    private final List<AbstractMappingException> failures = new ArrayList<>();

    /**
     * @param typeDefinitionRegistry   the schema
     * @param graphQLResolversRegistry the resolvers
     * @param processedTypes           a read-only live view of the Java class that each schema type is bound to: binding
     *                                 a field can reach a type that no field reached before
     * @param precedence               the configured precedence
     * @param manifestRecorder         the manifest sink
     * @param callbacks                the binding work that stays with the type phase
     */
    InterfaceFieldBinder(TypeDefinitionRegistry typeDefinitionRegistry, GraphQLResolversRegistry graphQLResolversRegistry,
                         Map<String, Class<?>> processedTypes, Precedence precedence,
                         BindingManifestRecorder manifestRecorder, Callbacks callbacks) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.processedTypes = processedTypes;
        this.precedence = precedence;
        this.callbacks = callbacks;
        this.ambiguityLog = new AmbiguityLog(precedence, manifestRecorder);
    }

    InterfaceBindings bind() {
        bindInterfaceFields();
        return new InterfaceBindings(Map.copyOf(fieldBindings), Map.copyOf(resolverBindings), ambiguityLog.snapshot(),
                List.copyOf(failures));
    }

    private List<InterfaceResolverMethod> findInterfaceResolverMethods(InterfaceField field) {
        List<InterfaceResolverMethod> result = new ArrayList<>();
        String fieldName = field.fieldDefinition().getName();

        for (InterfaceTypeDefinition interfaceTypeDefinition
                : getDeclaringInterfaces(typeDefinitionRegistry, field.objectTypeDefinition(), fieldName)) {
            Class<?> interfaceClass = processedTypes.get(interfaceTypeDefinition.getName());

            if (interfaceClass == null || interfaceClass.equals(field.ownSourceClass())) {
                continue;
            }

            for (BeanDefinitionAndMethod method
                    : graphQLResolversRegistry.findTypeExecutableMethods(interfaceClass,
                    interfaceTypeDefinition.getName(), fieldName)) {
                result.add(new InterfaceResolverMethod(interfaceTypeDefinition, interfaceClass, method));
            }
        }

        return result;
    }

    private void takeDiscoveredFields() {
        InterfaceFields fields = callbacks.takeDiscoveredFields();
        unboundInterfaceFields.addAll(fields.unbound());
        ownInterfaceFields.addAll(fields.own());
        deferredModelMemberFields.addAll(fields.deferred());
        interfaceResolverFields.addAll(fields.resolverBound());
    }

    private void bindInterfaceFields() {
        boolean rebound = true;
        while (rebound) {
            rebound = bindInterfaceFieldsOnce();
        }
    }

    private boolean bindInterfaceFieldsOnce() {
        while (true) {
            takeDiscoveredFields();
            boolean progress = false;
            for (InterfaceField field : List.copyOf(unboundInterfaceFields)) {
                List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
                if (methods.isEmpty()) {
                    continue;
                }

                unboundInterfaceFields.remove(field);
                progress = true;

                if (methods.size() > 1) {
                    failures.add(new MultipleMethodsFoundException(field.mappingContext(),
                            toInterfaceMethodsMap(methods)));
                    continue;
                }

                interfaceResolverFields.add(field);
                collectFailure(field.mappingContext(), () -> bindInterfaceField(field, methods.get(0)));
            }

            if (progress) {
                continue;
            }
            if (deferredModelMemberFields.isEmpty()) {
                break;
            }

            for (InterfaceField field : sortedByCoordinate(deferredModelMemberFields)) {
                deferredModelMemberFields.remove(field);
                List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
                if (methods.isEmpty()) {
                    ownInterfaceFields.add(field);
                    collectFailure(field.mappingContext(), () -> callbacks.bindModelMember(field));
                } else {
                    collectFailure(field.mappingContext(), () -> bindResolverOverModelMember(field, methods));
                }
            }
        }

        for (InterfaceField field : sortedByCoordinate(unboundInterfaceFields)) {
            unboundInterfaceFields.remove(field);
            if (field.modelMember() != null) {
                failures.add(new ResolverRequiredException(field.mappingContext(),
                        callbacks.resolverDirective(field.fieldDefinition()), field.ownMembers()));
            } else {
                failures.add(callbacks.methodNotFound(field.objectTypeDefinition().getName(), field.ownSourceClass(),
                        field.fieldDefinition().getName(), field.mappingContext()));
            }
        }

        for (InterfaceField field : sortedByCoordinate(interfaceResolverFields)) {
            List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
            if (methods.size() > 1) {
                interfaceResolverFields.remove(field);
                failures.add(new MultipleMethodsFoundException(field.mappingContext(),
                        toInterfaceMethodsMap(methods)));
            }
        }

        boolean rebound = false;
        for (InterfaceField field : sortedByCoordinate(ownInterfaceFields)) {
            List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
            if (methods.isEmpty()) {
                continue;
            }

            if (field.modelMember() == null) {
                Map<Class<?>, List<String>> members = toInterfaceMethodsMap(methods);
                field.ownMembers().forEach((clazz, names) ->
                        members.computeIfAbsent(clazz, it -> new ArrayList<>()).addAll(names));
                ownInterfaceFields.remove(field);
                failures.add(new MultipleMethodsFoundException(field.mappingContext(), members));
                continue;
            }

            switch (precedence) {
                case STRICT -> {
                    ownInterfaceFields.remove(field);
                    failures.add(new AmbiguousBindingException(field.mappingContext(),
                            AmbiguityLog.merge(field.ownMembers(), toInterfaceMethodsMap(methods))));
                }
                case PROPERTY_FIRST -> ambiguityLog.record(field.mappingContext(), field.modelMember(),
                        methods.stream().map(InterfaceResolverMethod::method).toList());
                case RESOLVER_FIRST -> {
                    ownInterfaceFields.remove(field);
                    collectFailure(field.mappingContext(), () -> bindResolverOverModelMember(field, methods));
                    rebound = true;
                }
                default -> throw new IllegalStateException("Unknown precedence " + precedence);
            }
        }

        return rebound;
    }

    private void bindResolverOverModelMember(InterfaceField field, List<InterfaceResolverMethod> methods) {
        if (methods.size() > 1) {
            throw new MultipleMethodsFoundException(field.mappingContext(), toInterfaceMethodsMap(methods));
        }
        ambiguityLog.record(field.mappingContext(), field.modelMember(),
                methods.stream().map(InterfaceResolverMethod::method).toList());
        interfaceResolverFields.add(field);
        bindInterfaceField(field, methods.get(0));
    }

    private void bindInterfaceField(InterfaceField field, InterfaceResolverMethod method) {
        BeanDefinitionAndMethod beanDefinitionAndMethod = method.method();

        String description = GraphQLResolversRegistry.describeMethod(
                beanDefinitionAndMethod.getBeanDefinition(), beanDefinitionAndMethod.getExecutableMethod());
        fieldBindings.put(field.coordinate(), "resolver " + description + " of interface "
                + method.interfaceTypeDefinition().getName());
        resolverBindings.computeIfAbsent(description, it -> new TreeSet<>())
                .add(method.interfaceTypeDefinition().getName() + "." + field.fieldDefinition().getName());

        callbacks.wireResolver(field, method);
    }

    private static Map<Class<?>, List<String>> toInterfaceMethodsMap(List<InterfaceResolverMethod> methods) {
        return toMap(methods.stream().map(InterfaceResolverMethod::method).toList());
    }

    private static List<InterfaceField> sortedByCoordinate(List<InterfaceField> fields) {
        return fields.stream().sorted(Comparator.comparing(InterfaceField::coordinate)).toList();
    }

    private void collectFailure(MappingContext mappingContext, Runnable action) {
        try {
            action.run();
        } catch (AbstractMappingException e) {
            failures.add(e);
        } catch (RuntimeException e) {
            failures.add(new UnexpectedBindingException(mappingContext, e));
        }
    }

    static List<InterfaceTypeDefinition> getDeclaringInterfaces(TypeDefinitionRegistry typeDefinitionRegistry,
                                                               ObjectTypeDefinition objectTypeDefinition,
                                                               String fieldName) {
        return objectTypeDefinition.getImplements().stream()
                .map(it -> typeDefinitionRegistry.getType(getTypeName(it).getName(), InterfaceTypeDefinition.class))
                .flatMap(Optional::stream)
                .filter(it -> it.getFieldDefinitions().stream().anyMatch(field -> field.getName().equals(fieldName)))
                .sorted(Comparator.comparing(InterfaceTypeDefinition::getName))
                .toList();
    }

    /**
     * What the phase needs from the type phase: binding a model member or a resolver method to a field is the same work
     * as for any other field.
     */
    interface Callbacks {

        /**
         * @return the fields that the type phase collected since the last call; it forgets them
         */
        InterfaceFields takeDiscoveredFields();

        void bindModelMember(InterfaceField field);

        void wireResolver(InterfaceField field, InterfaceResolverMethod method);

        MethodNotFoundException methodNotFound(String objectTypeName, Class<?> sourceClass, String fieldName,
                                               TypeMappingContext mappingContext);

        @Nullable
        String resolverDirective(FieldDefinition fieldDefinition);
    }

    /**
     * What the type phase collected for the interface fields.
     *
     * @param unbound       the fields that nothing of the object type binds
     * @param own           the fields that the object type binds itself
     * @param deferred      the model members that wait for the interface resolvers
     * @param resolverBound the fields that an interface resolver already binds
     */
    record InterfaceFields(List<InterfaceField> unbound, List<InterfaceField> own, List<InterfaceField> deferred,
                           List<InterfaceField> resolverBound) {
    }

    /**
     * What the phase bound.
     *
     * @param fieldBindings    how each coordinate was bound, by coordinate
     * @param resolverBindings the coordinates each resolver method was bound to, by method
     * @param ambiguities      the fields that both sides could bind
     * @param failures         the failures of the fields it could not bind
     */
    record InterfaceBindings(Map<String, String> fieldBindings, Map<String, Set<String>> resolverBindings,
                             Ambiguities ambiguities, List<AbstractMappingException> failures) {
    }

}
