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
package io.micronaut.graphql.tools.internal.wiring.interfaces;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration.Precedence;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules;
import io.micronaut.graphql.tools.internal.checks.Ambiguities;
import io.micronaut.graphql.tools.internal.checks.AmbiguityLog;
import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.AmbiguousBindingException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.ResolverRequiredException;
import io.micronaut.graphql.tools.internal.exceptions.UnexpectedBindingException;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.BeanDefinitionAndMethod;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static io.micronaut.graphql.tools.internal.MicronautUtils.toMap;

/**
 * The phase that binds the fields which an object type shares with the interfaces it implements: a field that no member of
 * the Java type and no resolver of the type can bind may be bound by a resolver of the interface, and the precedence
 * decides when both can. It works on its own lists, takes what the type phase collected, and again whatever binding a field
 * makes the type phase reach, and returns what it bound as one immutable result.
 */
@Internal
public final class InterfaceFieldBinder {

    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final Map<String, Class<?>> processedTypes;
    private final Precedence precedence;
    private final Callbacks callbacks;
    private final AmbiguityLog ambiguityLog;

    private final DeclaringInterfaces declaringInterfaces;
    // the fields that nothing binds yet, in the order they were collected, by sequence number
    private final NavigableMap<Long, WaitingField> waiting = new TreeMap<>();
    // the sequence numbers of the waiting fields whose answer may have changed since they were last asked
    private final NavigableSet<Long> dirty = new TreeSet<>();
    // the waiting fields that an interface not yet bound to a Java class could still bind, by interface name
    private final Map<String, List<WaitingField>> waitingOnInterface = new HashMap<>();
    // the sequence numbers of the waiting fields already listed under their unbound interfaces: once is enough
    private final Set<Long> listed = new HashSet<>();
    private final Map<String, Class<?>> unreturnedClasses = new HashMap<>();
    private final Set<String> boundUnreturned = new TreeSet<>();
    private long nextSequence;
    private final List<InterfaceField> interfaceResolverFields = new ArrayList<>();
    private final List<InterfaceField> ownInterfaceFields = new ArrayList<>();
    private final List<InterfaceField> deferredModelMemberFields = new ArrayList<>();
    private final Map<String, String> fieldBindings = new HashMap<>();
    private final Map<String, Set<String>> resolverBindings = new HashMap<>();
    private final List<AbstractMappingException> failures = new ArrayList<>();

    /**
     * @param declaringInterfaces      the interfaces that declare each field
     * @param graphQLResolversRegistry the resolvers
     * @param processedTypes           a read-only live view of the Java class that each schema type is bound to: binding
     *                                 a field can reach a type that no field reached before
     * @param precedence               the configured precedence
     * @param manifestRecorder         the manifest sink
     * @param callbacks                the binding work that stays with the type phase
     */
    public InterfaceFieldBinder(DeclaringInterfaces declaringInterfaces, GraphQLResolversRegistry graphQLResolversRegistry,
                         Map<String, Class<?>> processedTypes, Precedence precedence,
                         BindingManifestRecorder manifestRecorder, Callbacks callbacks) {
        this.declaringInterfaces = declaringInterfaces;
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.processedTypes = processedTypes;
        this.precedence = precedence;
        this.callbacks = callbacks;
        this.ambiguityLog = new AmbiguityLog(precedence, manifestRecorder);
    }

    public InterfaceBindings bind() {
        bindInterfaceFields();
        return new InterfaceBindings(Map.copyOf(fieldBindings), Map.copyOf(resolverBindings), ambiguityLog.snapshot(),
                List.copyOf(failures), Set.copyOf(boundUnreturned));
    }

    private List<InterfaceResolverMethod> findInterfaceResolverMethods(InterfaceField field) {
        List<InterfaceResolverMethod> result = new ArrayList<>();
        String fieldName = field.fieldDefinition().getName();

        for (InterfaceTypeDefinition interfaceTypeDefinition
                : declaringInterfaces.of(field.objectTypeDefinition(), fieldName)) {
            Class<?> interfaceClass = processedTypes.get(interfaceTypeDefinition.getName());
            boolean unreturned = interfaceClass == null;
            if (unreturned) {
                interfaceClass = unreturnedInterfaceClass(interfaceTypeDefinition, field.mappingContext());
            }

            if (interfaceClass == null || interfaceClass.equals(field.ownSourceClass())) {
                continue;
            }

            for (BeanDefinitionAndMethod method
                    : graphQLResolversRegistry.findTypeExecutableMethods(interfaceClass,
                    interfaceTypeDefinition.getName(), fieldName)) {
                result.add(new InterfaceResolverMethod(interfaceTypeDefinition, interfaceClass, method));
                if (unreturned) {
                    boundUnreturned.add(interfaceTypeDefinition.getName());
                }
            }
        }

        return result;
    }

    // an interface that no field returns is never processed, so its class comes from the dictionary or the derivation
    @Nullable
    private Class<?> unreturnedInterfaceClass(InterfaceTypeDefinition interfaceType, TypeMappingContext mappingContext) {
        if (unreturnedClasses.containsKey(interfaceType.getName())) {
            return unreturnedClasses.get(interfaceType.getName());
        }
        Class<?> found = null;
        try {
            found = callbacks.findInterfaceClass(interfaceType, mappingContext);
        } catch (AbstractMappingException e) {
            failures.add(e);
        }
        unreturnedClasses.put(interfaceType.getName(), found);
        return found;
    }

    private void takeDiscoveredFields() {
        InterfaceFields fields = callbacks.takeDiscoveredFields();
        fields.unbound().forEach(this::wait);
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

    private void wait(InterfaceField field) {
        WaitingField waitingField = new WaitingField(field, nextSequence++);
        waiting.put(waitingField.sequence(), waitingField);
        dirty.add(waitingField.sequence());
    }

    // a field is asked again only when an interface that declares it has been bound to a Java class since
    private void blockOnUnboundInterfaces(WaitingField waitingField) {
        if (!listed.add(waitingField.sequence())) {
            return;
        }
        InterfaceField field = waitingField.field();
        for (InterfaceTypeDefinition declaring
                : declaringInterfaces.of(field.objectTypeDefinition(), field.fieldDefinition().getName())) {
            if (!processedTypes.containsKey(declaring.getName())) {
                waitingOnInterface.computeIfAbsent(declaring.getName(), it -> new ArrayList<>()).add(waitingField);
            }
        }
    }

    private void wakeFieldsOfBoundInterfaces() {
        if (waitingOnInterface.isEmpty()) {
            return;
        }
        waitingOnInterface.entrySet().removeIf(entry -> {
            if (!processedTypes.containsKey(entry.getKey())) {
                return false;
            }
            for (WaitingField waitingField : entry.getValue()) {
                if (waiting.containsKey(waitingField.sequence())) {
                    dirty.add(waitingField.sequence());
                }
            }
            return true;
        });
    }

    private boolean bindInterfaceFieldsOnce() {
        while (true) {
            takeDiscoveredFields();
            wakeFieldsOfBoundInterfaces();
            boolean progress = false;
            long position = -1;
            Long next;
            while ((next = dirty.higher(position)) != null) {
                position = next;
                dirty.remove(next);
                WaitingField waitingField = waiting.get(next);
                InterfaceField field = waitingField.field();
                List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
                if (methods.isEmpty()) {
                    blockOnUnboundInterfaces(waitingField);
                    continue;
                }

                waiting.remove(next);
                progress = true;

                if (methods.size() > 1) {
                    failures.add(new MultipleMethodsFoundException(field.mappingContext(),
                            toInterfaceMethodsMap(methods)));
                } else {
                    interfaceResolverFields.add(field);
                    collectFailure(field.mappingContext(), () -> bindInterfaceField(field, methods.get(0)));
                }
                wakeFieldsOfBoundInterfaces();
            }

            if (progress) {
                continue;
            }
            if (deferredModelMemberFields.isEmpty()) {
                break;
            }

            List<InterfaceField> deferred = sortedByCoordinate(deferredModelMemberFields);
            deferredModelMemberFields.clear();
            for (InterfaceField field : deferred) {
                List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
                if (methods.isEmpty()) {
                    ownInterfaceFields.add(field);
                    collectFailure(field.mappingContext(), () -> callbacks.bindModelMember(field));
                } else {
                    collectFailure(field.mappingContext(), () -> bindResolverOverModelMember(field, methods));
                }
            }
        }

        List<InterfaceField> stillUnbound = sortedByCoordinate(waiting.values().stream()
                .map(WaitingField::field).toList());
        waiting.clear();
        dirty.clear();
        waitingOnInterface.clear();
        listed.clear();
        for (InterfaceField field : stillUnbound) {
            if (field.modelMember() != null) {
                failures.add(new ResolverRequiredException(field.mappingContext(),
                        callbacks.resolverDirective(field.fieldDefinition()), field.ownMembers()));
            } else {
                failures.add(callbacks.methodNotFound(field.objectTypeDefinition().getName(), field.ownSourceClass(),
                        field.fieldDefinition().getName(), field.mappingContext()));
            }
        }

        Set<InterfaceField> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (InterfaceField field : sortedByCoordinate(interfaceResolverFields)) {
            List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
            if (methods.size() > 1) {
                removed.add(field);
                failures.add(new MultipleMethodsFoundException(field.mappingContext(),
                        toInterfaceMethodsMap(methods)));
            }
        }
        interfaceResolverFields.removeIf(removed::contains);
        removed.clear();

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
                removed.add(field);
                failures.add(new MultipleMethodsFoundException(field.mappingContext(), members));
                continue;
            }

            boolean intent = methods.stream().anyMatch(it -> graphQLResolversRegistry.declaresCoordinate(it.method()));
            switch (PrecedenceRules.decide(PrecedenceRules.Setting.valueOf(precedence.name()), intent)) {
                case FAIL -> {
                    removed.add(field);
                    failures.add(new AmbiguousBindingException(field.mappingContext(),
                            AmbiguityLog.merge(field.ownMembers(), toInterfaceMethodsMap(methods))));
                }
                case MEMBER -> ambiguityLog.record(field.mappingContext(), field.modelMember(),
                        methods.stream().map(InterfaceResolverMethod::method).toList());
                case RESOLVER -> {
                    removed.add(field);
                    collectFailure(field.mappingContext(), () -> bindResolverOverModelMember(field, methods));
                    rebound = true;
                }
                default -> throw new IllegalStateException("Unknown precedence " + precedence);
            }
        }

        ownInterfaceFields.removeIf(removed::contains);
        return rebound;
    }

    private void bindResolverOverModelMember(InterfaceField field, List<InterfaceResolverMethod> methods) {
        if (methods.size() > 1) {
            throw new MultipleMethodsFoundException(field.mappingContext(), toInterfaceMethodsMap(methods));
        }
        if (methods.stream().noneMatch(it -> graphQLResolversRegistry.declaresCoordinate(it.method()))) {
            ambiguityLog.record(field.mappingContext(), field.modelMember(),
                    methods.stream().map(InterfaceResolverMethod::method).toList());
        }
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
        // the coordinate is built once per field, not once per comparison
        return fields.stream()
                .map(it -> Map.entry(it.coordinate(), it))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
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

    private record WaitingField(InterfaceField field, long sequence) {
    }

    /**
     * What the phase needs from the type phase: binding a model member or a resolver method to a field is the same work
     * as for any other field.
     */
    public interface Callbacks {

        /**
         * @return the fields that the type phase collected since the last call; it forgets them
         */
        InterfaceFields takeDiscoveredFields();

        void bindModelMember(InterfaceField field);

        void wireResolver(InterfaceField field, InterfaceResolverMethod method);

        MethodNotFoundException methodNotFound(String objectTypeName, Class<?> sourceClass, String fieldName,
                                               TypeMappingContext mappingContext);

        /**
         * @param interfaceType  an interface that no field returned, so the type phase bound no class to it
         * @param mappingContext where a failure is reported
         * @return the class that the dictionary or the derivation names for it, or null
         */
        @Nullable
        Class<?> findInterfaceClass(InterfaceTypeDefinition interfaceType, TypeMappingContext mappingContext);

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
    public record InterfaceFields(List<InterfaceField> unbound, List<InterfaceField> own, List<InterfaceField> deferred,
                           List<InterfaceField> resolverBound) {
    }

    /**
     * What the phase bound.
     *
     * @param fieldBindings    how each coordinate was bound, by coordinate
     * @param resolverBindings the coordinates each resolver method was bound to, by method
     * @param ambiguities      the fields that both sides could bind
     * @param failures         the failures of the fields it could not bind
     * @param boundUnreturned  the interfaces that no field returns and whose resolver bound a field
     */
    public record InterfaceBindings(Map<String, String> fieldBindings, Map<String, Set<String>> resolverBindings,
                             Ambiguities ambiguities, List<AbstractMappingException> failures,
                             Set<String> boundUnreturned) {
    }

}
