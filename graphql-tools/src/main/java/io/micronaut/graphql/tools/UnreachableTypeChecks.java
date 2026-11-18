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

import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.exceptions.InvalidAllowUnreachableException;
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.GraphQLUtils.getType;

/**
 * The reachability phase: the types that no field reachable from a root operation type returns, and the value types that
 * nothing binds, judged against {@code allow-unreachable} and {@code unreachable-types}.
 */
@Internal
final class UnreachableTypeChecks {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLToolsConfiguration configuration;
    private final BindingManifestRecorder manifestRecorder;

    UnreachableTypeChecks(TypeDefinitionRegistry typeDefinitionRegistry, GraphQLToolsConfiguration configuration,
                          BindingManifestRecorder manifestRecorder) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.configuration = configuration;
        this.manifestRecorder = manifestRecorder;
    }

    Reachability processUnreachableTypes(SchemaDefinition schemaDefinition, Set<String> implementedInterfaces,
                                         Map<String, Class<?>> processedTypes,
                                         RuntimeWiring.Builder runtimeWiringBuilder) {
        RuntimeException finding = null;
        Set<String> reachedTypeNames = new HashSet<>(processedTypes.keySet());
        reachedTypeNames.addAll(implementedInterfaces);
        for (OperationTypeDefinition operationTypeDefinition : schemaDefinition.getOperationTypeDefinitions()) {
            reachedTypeNames.add(operationTypeDefinition.getTypeName().getName());
        }

        Map<String, TypeDefinition<?>> unreachableTypes = new TreeMap<>();
        for (TypeDefinition<?> typeDefinition : typeDefinitionRegistry.types().values()) {
            boolean needsBinding = typeDefinition instanceof ObjectTypeDefinition
                    || typeDefinition instanceof InterfaceTypeDefinition
                    || typeDefinition instanceof UnionTypeDefinition;
            if (needsBinding && !reachedTypeNames.contains(typeDefinition.getName())) {
                unreachableTypes.put(typeDefinition.getName(), typeDefinition);
            }
        }

        Map<String, TypeDefinition<?>> unreachableValueTypes = findUnreachableValueTypes(processedTypes);
        boolean strict = configuration.isStrict();
        Map<String, TypeDefinition<?>> checked = new TreeMap<>(unreachableTypes);
        if (strict) {
            checked.putAll(unreachableValueTypes);
        }

        Set<String> allowed = new TreeSet<>(configuration.getAllowUnreachable());
        boolean ignoring = configuration.getUnreachableTypes() == GraphQLToolsConfiguration.UnreachableTypes.IGNORE;
        Set<String> covered = findCoveredValueTypes(ignoring ? unreachableTypes.keySet() : allowed);
        checked.keySet().removeAll(covered);
        List<String> undefined = allowed.stream()
                .filter(it -> !isDefined(it))
                .toList();
        List<String> notUnreachable = allowed.stream()
                .filter(it -> isDefined(it) && !unreachableTypes.containsKey(it)
                        && !unreachableValueTypes.containsKey(it))
                .toList();
        if (!undefined.isEmpty() || !notUnreachable.isEmpty()) {
            throw new InvalidAllowUnreachableException(undefined, notUnreachable,
                    "object, interface, union, enum, input or scalar");
        }

        Set<String> unbound;
        if (ignoring) {
            unbound = unreachableTypes.keySet();
            if (!checked.isEmpty()) {
                LOG.warn("{} is ignore: the schema defines types that no field reachable from a root operation "
                                + "type returns; they use graphql-java's default data fetchers: {}",
                        GraphQLToolsConfiguration.PREFIX + ".unreachable-types", String.join(", ", checked.keySet()));
            }
        } else {
            Map<String, String> failing = new LinkedHashMap<>();
            checked.forEach((name, typeDefinition) -> {
                if (!allowed.contains(name)) {
                    failing.put(name, getType(typeDefinition));
                }
            });
            if (failing.isEmpty()) {
                unbound = allowed.stream().filter(unreachableTypes::containsKey)
                        .collect(Collectors.toCollection(TreeSet::new));
            } else {
                finding = new UnreachableTypesException(failing);
                unbound = unreachableTypes.keySet();
            }
            if (!allowed.isEmpty()) {
                LOG.info("Unreachable types allowed by {} use graphql-java's default data fetchers: {}",
                        GraphQLToolsConfiguration.PREFIX + ".allow-unreachable", String.join(", ", allowed));
            }
        }

        unbound.forEach(it -> manifestRecorder.diagnostic("unreachable", it));
        unreachableValueTypes.keySet().forEach(it -> manifestRecorder.diagnostic("unreachable", it));

        for (String name : unbound) {
            TypeDefinition<?> typeDefinition = unreachableTypes.get(name);
            if (!(typeDefinition instanceof ObjectTypeDefinition)) {
                runtimeWiringBuilder.type(name, typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder
                        .typeResolver(env -> {
                            throw new IllegalStateException("No type resolver for the unreachable type " + name);
                        }));
            }
        }

        return new Reachability(unbound, finding);
    }

    private Set<String> findCoveredValueTypes(Set<String> roots) {
        Set<String> covered = new TreeSet<>();
        Deque<String> pending = new ArrayDeque<>(roots);
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!visited.add(name)) {
                continue;
            }
            TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(name).orElse(null);
            List<Type<?>> usedTypes = new ArrayList<>();
            if (typeDefinition instanceof ObjectTypeDefinition objectTypeDefinition) {
                collectFields(objectTypeDefinition.getFieldDefinitions(), usedTypes);
            } else if (typeDefinition instanceof InterfaceTypeDefinition interfaceTypeDefinition) {
                collectFields(interfaceTypeDefinition.getFieldDefinitions(), usedTypes);
            } else if (typeDefinition instanceof InputObjectTypeDefinition inputObjectTypeDefinition) {
                inputObjectTypeDefinition.getInputValueDefinitions().forEach(it -> usedTypes.add(it.getType()));
            }
            for (Type<?> usedType : usedTypes) {
                String usedName = GraphQLUtils.getTypeName(usedType).getName();
                TypeDefinition<?> used = typeDefinitionRegistry.getType(usedName).orElse(null);
                boolean valueType = used instanceof EnumTypeDefinition || used instanceof InputObjectTypeDefinition
                        || typeDefinitionRegistry.scalars().containsKey(usedName);
                if (valueType && covered.add(usedName)) {
                    pending.add(usedName);
                }
            }
        }
        return covered;
    }

    private static void collectFields(List<FieldDefinition> fieldDefinitions, List<Type<?>> usedTypes) {
        for (FieldDefinition fieldDefinition : fieldDefinitions) {
            usedTypes.add(fieldDefinition.getType());
            fieldDefinition.getInputValueDefinitions().forEach(it -> usedTypes.add(it.getType()));
        }
    }

    private Map<String, TypeDefinition<?>> findUnreachableValueTypes(Map<String, Class<?>> processedTypes) {
        Set<String> directiveTypes = GraphQLUtils.findDirectiveArgumentTypes(typeDefinitionRegistry);
        Map<String, TypeDefinition<?>> result = new TreeMap<>();
        for (TypeDefinition<?> typeDefinition : typeDefinitionRegistry.types().values()) {
            boolean valueType = typeDefinition instanceof EnumTypeDefinition
                    || typeDefinition instanceof InputObjectTypeDefinition;
            if (valueType && !processedTypes.containsKey(typeDefinition.getName())
                    && !directiveTypes.contains(typeDefinition.getName())) {
                result.put(typeDefinition.getName(), typeDefinition);
            }
        }
        typeDefinitionRegistry.scalars().forEach((name, definition) -> {
            if (!ScalarInfo.isGraphqlSpecifiedScalar(name) && !manifestRecorder.isScalarReached(name)
                    && !directiveTypes.contains(name)) {
                result.put(name, definition);
            }
        });
        return result;
    }

    private boolean isDefined(String typeName) {
        return typeDefinitionRegistry.types().containsKey(typeName)
                || typeDefinitionRegistry.scalars().containsKey(typeName);
    }

    /**
     * The result of the phase.
     *
     * @param unbound the unreachable types that stay without wiring
     * @param finding the failure for the unreachable types that nothing allows, or {@code null}
     */
    record Reachability(Set<String> unbound, @Nullable RuntimeException finding) {

        Reachability {
            unbound = Collections.unmodifiableSet(new TreeSet<>(unbound));
        }
    }

}
