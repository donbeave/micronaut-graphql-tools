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
package io.micronaut.graphql.tools.internal.checks;

import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.rules.EnumTypeRules;
import io.micronaut.graphql.tools.binding.internal.rules.EnumVerdict;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.BeanDefinitionAndMethod;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.internal.model.RuntimeTypeModel;
import io.micronaut.inject.ExecutableMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** The strict-mode checks of the binding: the resolver methods, fields and arguments that the schema does not use. */
@Internal
public final class StrictModeChecks {

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
    private final CheckPolicy policy;
    private final List<BindingCode> codes = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final Map<BindingCode, List<String>> warnings = new EnumMap<>(BindingCode.class);

  public   StrictModeChecks(BindingManifestRecorder manifestRecorder, CheckPolicy policy) {
        this.manifestRecorder = manifestRecorder;
        this.policy = policy;
    }

    public boolean enabled(BindingCode code) {
        return policy.enabled(code);
    }

    public void logWarnings() {
        warnings.forEach(policy::warn);
    }

    public List<String> getProblems() {
        return problems;
    }

    public List<BindingCode> getCodes() {
        return codes;
    }

    // records the finding in the manifest unless it is allowed, then fails or warns by the severity of the check
    private void finding(BindingCode code, String text, String... subject) {
        if (policy.allowed(BindingManifestRecorder.diagnosticLine(code, subject))) {
            return;
        }
        manifestRecorder.diagnostic(code, subject);
        if (policy.failing(code)) {
            codes.add(code);
            problems.add(text);
        } else {
            warnings.computeIfAbsent(code, it -> new ArrayList<>()).add(text);
        }
    }

    public void unboundResolverMethods(List<BeanDefinitionAndMethod> methods, Set<String> boundMethods) {
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
        findings.forEach((member, description) -> finding(BindingCode.UNBOUND_RESOLVER_METHOD,
                "The resolver method " + member + " binds no schema field. Remove it, make it non-public, "
                        + "or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.",
                member));
    }

    public void ignoredResolverMethods(List<BeanDefinitionAndMethod> methods) {
        for (BeanDefinitionAndMethod method : methods) {
            manifestRecorder.ignored(BindingManifestRecorder.methodMember(method.getExecutableMethod(),
                    method.getExecutableMethod().getDeclaringType()));
        }
    }

    public void rootMethodsOfSeveralOperationTypes(Map<ExecutableMethod<?, ?>, Set<String>> rootMethodBindings) {
        Map<String, Set<String>> findings = new TreeMap<>();
        rootMethodBindings.forEach((method, coordinates) -> {
            if (coordinates.stream().map(SchemaCoordinates::typeName).distinct().count() > 1) {
                findings.put(BindingManifestRecorder.methodMember(method, method.getDeclaringType()), coordinates);
            }
        });
        findings.forEach((member, coordinates) -> finding(BindingCode.ROOT_METHOD_SEVERAL_OPERATIONS,
                "The root resolver method " + member + " binds fields of several operation types: "
                        + String.join(", ", coordinates)
                        + ". Give each operation type its own method with @GraphQLSchemaCoordinate(\"Type.field\").",
                member));
    }

    private static boolean isObjectMethod(ExecutableMethod<?, ?> method) {
        return method.getDeclaringType() == Object.class
                || isObjectSignature(method.getMethodName(), method.getArgumentTypes());
    }

    public static boolean isObjectSignature(String name, Class<?>[] parameterTypes) {
        return OBJECT_METHOD_SIGNATURES.contains(signature(name, parameterTypes));
    }

    static String signature(String name, Class<?>[] parameterTypes) {
        return Arrays.stream(parameterTypes)
                .map(Class::getName)
                .collect(Collectors.joining(",", name + "(", ")"));
    }

    public void unboundEnumConstants(Map<String, Class<?>> boundTypes, TypeDefinitionRegistry registry,
                              RuntimeTypeModel typeModel) {
        new TreeMap<>(boundTypes).forEach((name, enumClass) -> {
            EnumTypeDefinition enumDefinition = registry.getType(name, EnumTypeDefinition.class).orElse(null);
            if (enumDefinition == null) {
                return;
            }
            EnumVerdict verdict = EnumTypeRules.check(
                    enumDefinition.getEnumValueDefinitions().stream().map(EnumValueDefinition::getName).toList(),
                    typeModel.of(enumClass));
            verdict.ignoredConstants().forEach(it -> manifestRecorder.ignored(enumClass.getTypeName() + "#" + it));
            if (!enabled(BindingCode.UNBOUND_ENUM_CONSTANT)) {
                return;
            }
            verdict.unboundConstants().forEach(constant -> {
                String member = enumClass.getTypeName() + "#" + constant;
                finding(BindingCode.UNBOUND_ENUM_CONSTANT, "The enum constant " + member
                        + " has no value in the schema enum " + name + ". Add the value to the schema or remove the "
                        + "constant.", member);
            });
        });
    }

    public void unusedDictionaryEntries(Map<String, Class<?>> dictionary, Collection<String> boundTypes,
                                 Set<String> allowedTypes) {
        new TreeMap<>(dictionary).forEach((name, clazz) -> {
            if (!boundTypes.contains(name) && !allowedTypes.contains(name)) {
                finding(BindingCode.UNUSED_DICTIONARY_ENTRY, "The SchemaMappingDictionary maps " + name + " to "
                        + clazz.getTypeName() + ", but the schema binds no type " + name
                        + ". Remove the entry or correct its name.", name + " = " + clazz.getTypeName());
            }
        });
    }

    public void interfacesReadThroughImplementations(Map<String, List<Class<?>>> interfaces) {
        interfaces.forEach((name, classes) -> finding(BindingCode.INTERFACE_READ_THROUGH_IMPLEMENTATION,
                "The type " + name + " is mapped to the interface " + classes.get(0).getName()
                        + ", which has no bean introspection of its own, so its properties are read from the "
                        + "implementation " + classes.get(1).getName() + ". Annotate the interface with "
                        + "@GraphQLType or @Introspected.", name));
    }

}
