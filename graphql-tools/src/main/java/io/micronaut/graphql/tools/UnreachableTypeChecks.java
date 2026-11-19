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
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.exceptions.InvalidAllowUnreachableException;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import io.micronaut.graphql.tools.internal.exceptions.UnreachableTypesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static io.micronaut.graphql.tools.GraphQLUtils.getType;

/**
 * The reachability phase: the types that no field reachable from a root operation type returns, and the value types that
 * nothing binds, judged against {@code allow.unreachable} and the severity of the two checks.
 */
@Internal
final class UnreachableTypeChecks {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLToolsConfiguration configuration;
    private final CheckPolicy policy;
    private final BindingManifestRecorder manifestRecorder;

    UnreachableTypeChecks(TypeDefinitionRegistry typeDefinitionRegistry, GraphQLToolsConfiguration configuration,
                          CheckPolicy policy, BindingManifestRecorder manifestRecorder) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.configuration = configuration;
        this.policy = policy;
        this.manifestRecorder = manifestRecorder;
    }

    Reachability processUnreachableTypes(SchemaDefinition schemaDefinition, Set<String> implementedInterfaces,
                                         Map<String, Class<?>> processedTypes,
                                         RuntimeWiring.Builder runtimeWiringBuilder) {
        SchemaBindingException finding = null;
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

        boolean typesOn = policy.enabled(BindingCode.UNREACHABLE_TYPE);
        boolean valueTypesOn = policy.enabled(BindingCode.UNREACHABLE_VALUE_TYPE);
        Set<String> allowed = new TreeSet<>(configuration.getAllow().getUnreachable());
        Map<String, TypeDefinition<?>> unreachableValueTypes = valueTypesOn || !allowed.isEmpty()
                ? findUnreachableValueTypes(processedTypes) : Map.of();

        List<String> undefined = allowed.stream()
                .filter(it -> !isDefined(it))
                .toList();
        Map<String, String> notUnreachable = new LinkedHashMap<>();
        for (String name : allowed) {
            if (isDefined(name) && !unreachableTypes.containsKey(name) && !unreachableValueTypes.containsKey(name)) {
                notUnreachable.put(name, whyNotUnreachable(name, reachedTypeNames));
            }
        }
        if (!undefined.isEmpty() || !notUnreachable.isEmpty()) {
            throw new InvalidAllowUnreachableException(undefined, notUnreachable,
                    "object, interface, union, enum, input or scalar");
        }

        // the types that the accepted types use are accepted with them
        Set<String> accepted = new TreeSet<>(allowed);
        if (!policy.failing(BindingCode.UNREACHABLE_TYPE)) {
            accepted.addAll(unreachableTypes.keySet());
        }
        Set<String> covered = valueTypesOn ? findCoveredValueTypes(accepted) : Set.of();

        Map<String, String> failing = new TreeMap<>();
        Map<BindingCode, List<String>> warnings = new EnumMap<>(BindingCode.class);
        if (typesOn) {
            judge(BindingCode.UNREACHABLE_TYPE, unreachableTypes, allowed, Set.of(), failing, warnings);
        }
        if (valueTypesOn) {
            judge(BindingCode.UNREACHABLE_VALUE_TYPE, unreachableValueTypes, allowed, covered, failing, warnings);
        }
        warnings.forEach(policy::warn);
        if (!failing.isEmpty()) {
            finding = new UnreachableTypesException(failing);
        } else if (!allowed.isEmpty()) {
            LOG.info("Unreachable types allowed by {} use graphql-java's default data fetchers: {}",
                    GraphQLToolsConfiguration.PREFIX + ".allow.unreachable", String.join(", ", allowed));
        }
        Set<String> unbound = unreachableTypes.keySet();

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

    // records each finding of the check and sorts it into failing or warnings, unless it is allowed or covered
    private void judge(BindingCode code, Map<String, TypeDefinition<?>> types, Set<String> allowed,
                       Set<String> covered, Map<String, String> failing, Map<BindingCode, List<String>> warnings) {
        types.forEach((name, typeDefinition) -> {
            if (policy.allowed(BindingManifestRecorder.diagnosticLine(code, name))) {
                return;
            }
            manifestRecorder.diagnostic(code, name);
            if (allowed.contains(name) || covered.contains(name)) {
                return;
            }
            if (policy.failing(code)) {
                failing.put(name, getType(typeDefinition));
            } else {
                warnings.computeIfAbsent(code, it -> new ArrayList<>()).add(name + " (" + getType(typeDefinition) + ")");
            }
        });
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

    // the reason that a listed type is not unreachable, for the message of the allow.unreachable entry: the types that
    // are left after the unreachable ones are the reached ones, the built-in scalars and the directive argument types
    private String whyNotUnreachable(String name, Set<String> reachedTypeNames) {
        if (ScalarInfo.isGraphqlSpecifiedScalar(name)) {
            return "is a built-in scalar";
        }
        if (reachedTypeNames.contains(name) || manifestRecorder.isScalarReached(name)) {
            return "is reachable";
        }
        return "is used by a directive";
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
    record Reachability(Set<String> unbound, @Nullable SchemaBindingException finding) {

        Reachability {
            unbound = Collections.unmodifiableSet(new TreeSet<>(unbound));
        }
    }

}
