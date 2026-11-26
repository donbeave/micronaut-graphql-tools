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
package io.micronaut.graphql.tools.internal.wiring;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.BeanDefinitionAndMethod;
import io.micronaut.graphql.tools.internal.resolver.ResolverInstances;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.inject.ExecutableMethod;
import java.util.List;
import java.util.TreeSet;

import static io.micronaut.graphql.tools.internal.MicronautUtils.toMap;

/** Binds the fields of the operation types (query, mutation, subscription) to the root resolver methods. */
@Internal
final class RootFieldBinder {

    private final WiringState s;
    private final BindingWalk walk;
    private final FieldFetchers fetchers;

    RootFieldBinder(WiringState s, BindingWalk walk, FieldFetchers fetchers) {
        this.s = s;
        this.walk = walk;
        this.fetchers = fetchers;
    }

    void processOperationTypeDefinition(OperationTypeDefinition operationTypeDefinition) {
        ObjectTypeDefinition objectTypeDefinition = s.typeDefinitionRegistry
                .getType(operationTypeDefinition.getTypeName(), ObjectTypeDefinition.class).get();

        s.manifestRecorder.type(objectTypeDefinition.getName(), "object", null);

        s.rootRuntimeWiringBuilder.type(operationTypeDefinition.getTypeName().getName(), typeRuntimeWiringBuilder -> {
            for (FieldDefinition fieldDefinition : objectTypeDefinition.getFieldDefinitions()) {
                walk.collectFailure(TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName()),
                        () -> processRootFieldDefinition(operationTypeDefinition.getName(), objectTypeDefinition,
                        fieldDefinition, typeRuntimeWiringBuilder));
            }

            return typeRuntimeWiringBuilder;
        });
    }

    void processRootFieldDefinition(String operation, ObjectTypeDefinition objectTypeDefinition,
                                            FieldDefinition fieldDefinition, TypeRuntimeWiring.Builder typeRuntimeWiringBuilder) {
        if (s.isClaimed(objectTypeDefinition, fieldDefinition)) {
            return;
        }

        TypeMappingContext mappingContext =
                TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        List<BeanDefinitionAndMethod> beanDefinitionAndMethods;
        try {
            beanDefinitionAndMethods = s.graphQLResolversRegistry.getRootExecutableMethods(
                    operation, objectTypeDefinition.getName(), fieldDefinition.getName(), mappingContext);
        } catch (MethodNotFoundException e) {
            throw s.hintClaimedFields(e, objectTypeDefinition.getName(), fieldDefinition.getName());
        }

        if (beanDefinitionAndMethods.size() > 1) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanDefinitionAndMethods));
        }

        BeanDefinitionAndMethod beanDefinitionAndMethod = beanDefinitionAndMethods.get(0);

        s.recordResolverBinding(mappingContext, beanDefinitionAndMethod);

        ExecutableMethod<Object, ?> executable = beanDefinitionAndMethod.getExecutableMethod();

        fetchers.processExecutableMethod(
                executable,
                executable.getReturnType(),
                null,
                ResolverInstances.of(s.context, beanDefinitionAndMethod.getBeanDefinition()),
                typeRuntimeWiringBuilder,
                mappingContext,
                null
        );

        s.rootMethodBindings.computeIfAbsent(executable, it -> new TreeSet<>())
                .add(objectTypeDefinition.getName() + "." + fieldDefinition.getName());

        if (s.manifestRecorder.isRecording()) {
            s.manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_ROOT,
                    () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                    executable.getReturnType().asArgument(),
                    objectTypeDefinition.getName().equals(s.subscriptionTypeName), FieldFetchers.isSingleResult(executable));
        }
    }
}
