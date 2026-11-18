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
import graphql.language.EnumValueDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Internal
final class StrictModeChecks {

    static final Set<String> OBJECT_METHOD_SIGNATURES = Set.of(
            "equals(java.lang.Object)",
            "hashCode()",
            "toString()",
            "getClass()",
            "notify()",
            "notifyAll()",
            "wait()",
            "wait(long)",
            "wait(long,int)");

    private final BindingManifestRecorder manifestRecorder;
    private final List<String> problems = new ArrayList<>();

    StrictModeChecks(BindingManifestRecorder manifestRecorder) {
        this.manifestRecorder = manifestRecorder;
    }

    List<String> getProblems() {
        return problems;
    }

    void unboundResolverMethods(List<BeanDefinitionAndMethod> methods, Set<String> boundMethods) {
        Map<String, String> findings = new TreeMap<>();
        for (BeanDefinitionAndMethod method : methods) {
            if (isObjectMethod(method.getExecutableMethod())) {
                continue;
            }
            String description = GraphQLResolversRegistry.describeMethod(method.getBeanDefinition(),
                    method.getExecutableMethod());
            if (!boundMethods.contains(description)) {
                findings.put(BindingManifestRecorder.methodMember(method.getExecutableMethod(),
                        method.getExecutableMethod().getDeclaringType()), description);
            }
        }
        findings.forEach((member, description) -> {
            manifestRecorder.diagnostic("unbound", member);
            problems.add("The resolver method " + member + " binds no schema field. Remove it, make it non-public, "
                    + "or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.");
        });
    }

    void ignoredResolverMethods(List<BeanDefinitionAndMethod> methods) {
        for (BeanDefinitionAndMethod method : methods) {
            manifestRecorder.ignored(BindingManifestRecorder.methodMember(method.getExecutableMethod(),
                    method.getExecutableMethod().getDeclaringType()));
        }
    }

    void rootMethodsOfSeveralOperationTypes(Map<ExecutableMethod<?, ?>, Set<String>> rootMethodBindings) {
        Map<String, Set<String>> findings = new TreeMap<>();
        rootMethodBindings.forEach((method, coordinates) -> {
            if (coordinates.size() > 1) {
                findings.put(BindingManifestRecorder.methodMember(method, method.getDeclaringType()), coordinates);
            }
        });
        findings.forEach((member, coordinates) -> problems.add("The root resolver method " + member
                + " binds fields of several operation types: " + String.join(", ", coordinates)
                + ". Give each operation type its own method with @GraphQLSchemaCoordinate(\"Type.field\")."));
    }

    private static boolean isObjectMethod(ExecutableMethod<?, ?> method) {
        return method.getDeclaringType() == Object.class
                || isObjectSignature(method.getMethodName(), method.getArgumentTypes());
    }

    static boolean isObjectSignature(String name, Class<?>[] parameterTypes) {
        return OBJECT_METHOD_SIGNATURES.contains(signature(name, parameterTypes));
    }

    static String signature(String name, Class<?>[] parameterTypes) {
        return Arrays.stream(parameterTypes)
                .map(Class::getName)
                .collect(Collectors.joining(",", name + "(", ")"));
    }

    void unboundEnumConstants(Map<String, Class<?>> boundTypes, TypeDefinitionRegistry registry) {
        new TreeMap<>(boundTypes).forEach((name, enumClass) -> {
            EnumTypeDefinition enumDefinition = registry.getType(name, EnumTypeDefinition.class).orElse(null);
            if (enumDefinition == null) {
                return;
            }
            Set<String> values = enumDefinition.getEnumValueDefinitions().stream()
                    .map(EnumValueDefinition::getName)
                    .collect(Collectors.toSet());
            EnumBeanIntrospection<?> introspection = EnumIntrospections.find(enumClass).orElse(null);
            if (introspection == null) {
                return;
            }
            List<String> missing = EnumIntrospections.constantsByName(introspection).keySet().stream()
                    .filter(it -> !values.contains(it))
                    .toList();
            Set<String> ignored = missing.isEmpty() ? Set.of() : ignoredEnumConstants(introspection);
            missing.stream()
                    .filter(it -> {
                        if (!ignored.contains(it)) {
                            return true;
                        }
                        manifestRecorder.ignored(enumClass.getTypeName() + "#" + it);
                        return false;
                    })
                    .sorted()
                    .forEach(constant -> {
                        String member = enumClass.getTypeName() + "#" + constant;
                        manifestRecorder.diagnostic("unbound", member);
                        problems.add("The enum constant " + member + " has no value in the schema enum " + name
                                + ". Add the value to the schema or remove the constant.");
                    });
        });
    }

    private static Set<String> ignoredEnumConstants(EnumBeanIntrospection<?> introspection) {
        Set<String> names = new HashSet<>();
        introspection.getConstants().stream()
                .filter(it -> it.hasAnnotation(GraphQLIgnore.class))
                .forEach(it -> names.add(((Enum<?>) it.getValue()).name()));
        return names;
    }

    void unusedDictionaryEntries(Map<String, Class<?>> dictionary, Collection<String> boundTypes,
                                 Set<String> allowedTypes) {
        new TreeMap<>(dictionary).forEach((name, clazz) -> {
            if (!boundTypes.contains(name) && !allowedTypes.contains(name)) {
                manifestRecorder.diagnostic("unusedDictionary", name + " = " + clazz.getTypeName());
                problems.add("The SchemaMappingDictionary maps " + name + " to " + clazz.getTypeName()
                        + ", but the schema binds no type " + name + ". Remove the entry or correct its name.");
            }
        });
    }

    void interfacesReadThroughImplementations(Map<String, List<Class<?>>> interfaces) {
        interfaces.forEach((name, classes) -> problems.add("The type " + name + " is mapped to the interface "
                + classes.get(0).getName() + ", which has no bean introspection of its own, so its properties are "
                + "read from the implementation " + classes.get(1).getName() + ". Annotate the interface with "
                + "@GraphQLType or @Introspected."));
    }

}
