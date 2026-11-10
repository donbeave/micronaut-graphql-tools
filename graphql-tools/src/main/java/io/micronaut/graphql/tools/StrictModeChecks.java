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
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The checks of strict mode that need the complete binding: each finding becomes a {@code !} line of the binding
 * manifest, whether or not strict mode is on, and a problem that strict mode fails on.
 */
@Internal
final class StrictModeChecks {

    private final BindingManifestRecorder manifestRecorder;
    private final List<String> problems = new ArrayList<>();

    StrictModeChecks(BindingManifestRecorder manifestRecorder) {
        this.manifestRecorder = manifestRecorder;
    }

    /**
     * @return one line per finding, in the order the checks ran, each sorted
     */
    List<String> getProblems() {
        return problems;
    }

    /**
     * A public method of a resolver bean that binds no field is either dead or bound by a name that no longer matches
     * the schema. On a bean that uses {@code @GraphQLSchemaCoordinate}, only the annotated methods count.
     *
     * @param methods      the methods that can bind a field
     * @param boundMethods the methods bound to a field or left unbound by the precedence, as
     *                     {@link GraphQLResolversRegistry#describeMethod} names them
     */
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

    /**
     * One root resolver method without a coordinate binds the field of its name on every root operation type; a
     * subscription and a query rarely want the same method.
     *
     * @param rootMethodBindings key: root resolver method, value: the root fields it is bound to
     */
    void rootMethodsOfSeveralOperationTypes(Map<String, Set<String>> rootMethodBindings) {
        rootMethodBindings.forEach((member, coordinates) -> {
            if (coordinates.size() > 1) {
                problems.add("The root resolver method " + member + " binds fields of several operation types: "
                        + String.join(", ", coordinates) + ". Give each operation type its own method with "
                        + "@GraphQLSchemaCoordinate(\"Type.field\").");
            }
        });
    }

    /**
     * @return {@code true} for a method that overrides or is a public method of {@link Object}, such as
     * {@code toString()}: it cannot be made non-public and binds no field by design
     */
    private static boolean isObjectMethod(ExecutableMethod<?, ?> method) {
        if (method.getDeclaringType() == Object.class) {
            return true;
        }
        try {
            Object.class.getMethod(method.getMethodName(), method.getArgumentTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * A Java enum constant without a value of the schema enum cannot be returned (graphql-java fails to serialize it)
     * and never arrives as an argument.
     *
     * @param boundTypes the bound types and their Java types, by name
     * @param registry   the schema definitions
     */
    void unboundEnumConstants(Map<String, Class<?>> boundTypes, TypeDefinitionRegistry registry) {
        new TreeMap<>(boundTypes).forEach((name, enumClass) -> {
            EnumTypeDefinition enumDefinition = registry.getType(name, EnumTypeDefinition.class).orElse(null);
            if (enumDefinition == null) {
                return;
            }
            Set<String> values = enumDefinition.getEnumValueDefinitions().stream()
                    .map(EnumValueDefinition::getName)
                    .collect(Collectors.toSet());
            Arrays.stream(enumClass.getEnumConstants())
                    .map(it -> ((Enum<?>) it).name())
                    .filter(it -> !values.contains(it))
                    .sorted()
                    .forEach(constant -> {
                        String member = enumClass.getTypeName() + "#" + constant;
                        manifestRecorder.diagnostic("unbound", member);
                        problems.add("The enum constant " + member + " has no value in the schema enum " + name
                                + ". Add the value to the schema or remove the constant.");
                    });
        });
    }

    /**
     * A {@link SchemaMappingDictionary} entry for a type that is not bound is stale or misspelled. Entries for types
     * that are unreachable and allowed are expected.
     *
     * @param dictionary   the dictionary entries
     * @param boundTypes   the names of the bound types
     * @param allowedTypes the unreachable types that are allowed
     */
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

    /**
     * An object type mapped to a Java interface that has no bean introspection of its own is read through the
     * introspection of its one implementation, so a getter that only the implementation declares binds silently.
     *
     * @param interfaces key: object type, value: the interface and the implementation whose introspection is read
     */
    void interfacesReadThroughImplementations(Map<String, List<Class<?>>> interfaces) {
        interfaces.forEach((name, classes) -> problems.add("The type " + name + " is mapped to the interface "
                + classes.get(0).getName() + ", which has no bean introspection of its own, so its properties are "
                + "read from the implementation " + classes.get(1).getName() + ". Annotate the interface with "
                + "@GraphQLType or @Introspected."));
    }

}
