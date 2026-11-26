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

import io.micronaut.graphql.tools.internal.wiring.interfaces.InterfaceField;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.execution.FieldExecutionKind;
import io.micronaut.graphql.tools.internal.checks.AmbiguityLog;
import io.micronaut.graphql.tools.internal.exceptions.mapping.IncorrectClassMappingException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.AmbiguousBindingException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.ExecuteOnException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.ResolverRequiredException;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.BeanDefinitionAndMethod;
import io.micronaut.graphql.tools.internal.model.ModelMember;
import io.micronaut.graphql.tools.internal.model.ModelMemberIndex;
import io.micronaut.graphql.tools.internal.resolver.MicronautPropertyDataFetcher;
import io.micronaut.graphql.tools.internal.resolver.ResolverInstances;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.annotation.ExecuteOn;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.internal.MicronautUtils.getExecutableMethodFullName;
import static io.micronaut.graphql.tools.internal.MicronautUtils.getPropertyMethodName;
import static io.micronaut.graphql.tools.internal.MicronautUtils.toMap;

/** Binds the fields of an object type to a bean property, a model method or a type resolver method. */
@Internal
final class ObjectFieldBinder {

    private final WiringState s;
    private final TypeBinder types;
    private final FieldFetchers fetchers;
    private final ResolverDirectives directives;

    ObjectFieldBinder(WiringState s, TypeBinder types, FieldFetchers fetchers, ResolverDirectives directives) {
        this.s = s;
        this.types = types;
        this.fetchers = fetchers;
        this.directives = directives;
    }

    boolean isIgnored(AnnotationMetadataProvider member, String name) {
        if (!member.getAnnotationMetadata().hasAnnotation(GraphQLIgnore.class)) {
            return false;
        }
        s.manifestRecorder.ignored(name);
        return true;
    }

    void processFieldDefinition(FieldDefinition fieldDefinition, ObjectTypeDefinition objectTypeDefinition,
                                        TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                        BeanIntrospection<Object> beanIntrospection) {
        if (s.isClaimed(objectTypeDefinition, fieldDefinition)) {
            return;
        }

        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        ModelMemberIndex members = s.memberIndexes.computeIfAbsent(beanIntrospection, ModelMemberIndex::new);
        List<BeanProperty<Object, Object>> beanProperties = members.properties(fieldDefinition.getName()).stream()
                .filter(it -> !isIgnored(it, BindingManifestRecorder.propertyMember(it)))
                .toList();
        List<BeanMethod<Object, ?>> beanMethods = members.methods(fieldDefinition.getName()).stream()
                .filter(it -> !isIgnored(it, BindingManifestRecorder.methodMember(it,
                        beanIntrospection.getBeanType())))
                .collect(Collectors.toList());

        if (beanProperties.size() + beanMethods.size() > 1) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanMethods, beanProperties));
        }
        Optional<BeanProperty<Object, Object>> beanProperty = beanProperties.stream().findFirst();

        List<InterfaceTypeDefinition> declaringInterfaces =
                s.interfaceIndex.of(objectTypeDefinition, fieldDefinition.getName());

        Class<?> sourceClass = s.graphQLBeanIntrospectionRegistry.getInterfaceClass(beanIntrospection.getBeanType());

        if (beanProperty.isPresent() || !beanMethods.isEmpty()) {
            ModelMember modelMember = new ModelMember(beanIntrospection, beanProperty.orElse(null),
                    beanMethods.isEmpty() ? null : beanMethods.get(0));
            processModelMemberField(objectTypeDefinition, fieldDefinition, typeRuntimeWiringBuilder, modelMember,
                    sourceClass, declaringInterfaces);
            return;
        }

        if (sourceClass.isPrimitive() || sourceClass.isEnum() || sourceClass.isAnnotation()) {
            throw IncorrectClassMappingException.forField(
                    IncorrectClassMappingException.MappingType.DETECT_TYPE,
                    IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                    mappingContext,
                    sourceClass,
                    null
            );
        }

        List<BeanDefinitionAndMethod> beanDefinitionAndMethods = s.graphQLResolversRegistry
                .findTypeExecutableMethods(sourceClass, objectTypeDefinition.getName(), fieldDefinition.getName());

        if (beanDefinitionAndMethods.isEmpty() && !declaringInterfaces.isEmpty()) {
            s.unboundInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition,
                    sourceClass, Collections.emptyMap(), null));
            return;
        }

        if (beanDefinitionAndMethods.isEmpty()) {
            throw methodNotFound(objectTypeDefinition.getName(), sourceClass, fieldDefinition.getName(),
                    mappingContext);
        }

        bindTypeResolver(objectTypeDefinition, fieldDefinition, typeRuntimeWiringBuilder, sourceClass,
                beanDefinitionAndMethods, declaringInterfaces);
    }

    void processModelMemberField(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                         TypeRuntimeWiring.Builder typeRuntimeWiringBuilder, ModelMember modelMember,
                                         Class<?> sourceClass, List<InterfaceTypeDefinition> declaringInterfaces) {
        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());
        String directive = directives.find(fieldDefinition);
        PrecedenceRules.Setting setting = PrecedenceRules.Setting.valueOf(s.configuration.getPrecedence().name());

        List<BeanDefinitionAndMethod> resolvers = sourceClass.isPrimitive() || sourceClass.isEnum()
                || sourceClass.isAnnotation()
                ? Collections.emptyList()
                : s.graphQLResolversRegistry.findTypeExecutableMethods(sourceClass, objectTypeDefinition.getName(),
                        fieldDefinition.getName());

        if (!resolvers.isEmpty()) {
            boolean intent = directive != null || resolvers.stream().anyMatch(s.graphQLResolversRegistry::declaresCoordinate);
            PrecedenceRules.Outcome outcome = PrecedenceRules.decide(setting, intent);
            if (outcome == PrecedenceRules.Outcome.FAIL) {
                throw new AmbiguousBindingException(mappingContext, AmbiguityLog.merge(modelMember.members(), toMap(resolvers)));
            }
            if (!intent) {
                s.ambiguityLog.record(mappingContext, modelMember, resolvers);
            }
            if (outcome == PrecedenceRules.Outcome.MEMBER) {
                if (!declaringInterfaces.isEmpty()) {
                    s.ownInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                            modelMember.members(), modelMember));
                }
                bindModelMember(objectTypeDefinition, fieldDefinition, typeRuntimeWiringBuilder, modelMember);
                return;
            }
            bindTypeResolver(objectTypeDefinition, fieldDefinition, typeRuntimeWiringBuilder, sourceClass, resolvers,
                    declaringInterfaces);
            return;
        }

        if (!declaringInterfaces.isEmpty() && directive != null) {
            s.unboundInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                    modelMember.members(), modelMember));
            return;
        }

        if (!declaringInterfaces.isEmpty() && (setting == PrecedenceRules.Setting.RESOLVER_FIRST
                || declaresInterfaceCoordinate(declaringInterfaces, fieldDefinition.getName()))) {
            s.deferredModelMemberFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                    modelMember.members(), modelMember));
            return;
        }

        if (directive != null) {
            throw new ResolverRequiredException(mappingContext, directive, modelMember.members());
        }

        if (!declaringInterfaces.isEmpty()) {
            s.ownInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                    modelMember.members(), modelMember));
        }
        bindModelMember(objectTypeDefinition, fieldDefinition, typeRuntimeWiringBuilder, modelMember);
    }

    boolean declaresInterfaceCoordinate(List<InterfaceTypeDefinition> interfaces, String fieldName) {
        return interfaces.stream().anyMatch(it -> s.graphQLResolversRegistry.isCoordinateDeclared(
                it.getName() + "." + fieldName));
    }

    void bindModelMember(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                 TypeRuntimeWiring.Builder typeRuntimeWiringBuilder, ModelMember modelMember) {
        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());
        BeanIntrospection<Object> beanIntrospection = modelMember.beanIntrospection();
        String coordinate = objectTypeDefinition.getName() + "." + fieldDefinition.getName();

        if (modelMember.beanProperty() != null) {
            BeanProperty<Object, Object> beanProperty = modelMember.beanProperty();

            mappingContext = TypeMappingContext.forField(
                    mappingContext,
                    beanIntrospection.getBeanType(),
                    getPropertyMethodName(beanProperty)
            );

            s.fieldBindings.put(coordinate, "property " + beanIntrospection.getBeanType().getName() + "."
                    + getPropertyMethodName(beanProperty));

            s.argumentPlanner.checkArgumentCount(beanProperty, mappingContext);

            if (beanProperty.getAnnotationMetadata().hasDeclaredAnnotation(ExecuteOn.class)) {
                throw new ExecuteOnException(BindingCode.EXECUTE_ON_PROPERTY, "@ExecuteOn is on an accessor that binds this field "
                        + "as a property, and a property is read, never run on another executor. Fix: make it a @GraphQLField "
                        + "method, or move the field to a type resolver, and put @ExecuteOn there.", mappingContext);
            }

            typeRuntimeWiringBuilder.dataFetcher(fieldDefinition.getName(), s.fieldDecorators.apply(
                    new FieldDescriptor(FieldFetchers.coordinates(mappingContext), FieldExecutionKind.TRIVIAL, FieldFetchers.hasArguments(mappingContext), null,
                            beanProperty.getAnnotationMetadata(),
                            beanIntrospection.getBeanType().getName() + " " + getPropertyMethodName(beanProperty)),
                    new MicronautPropertyDataFetcher(beanProperty)));

            types.processFieldReturnType(beanProperty.asArgument(), fieldDefinition.getType(), mappingContext);

            if (s.manifestRecorder.isRecording()) {
                s.manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                        () -> BindingManifestRecorder.propertyMember(beanProperty), beanProperty.asArgument(), false, false);
            }
            return;
        }

        BeanMethod<Object, ?> beanMethod = modelMember.beanMethod();

        s.fieldBindings.put(coordinate, "method " + beanIntrospection.getBeanType().getName() + "."
                + getExecutableMethodFullName(beanMethod));

        fetchers.processExecutableMethod(
                beanMethod,
                beanMethod.getReturnType(),
                null,
                null,
                typeRuntimeWiringBuilder,
                mappingContext,
                beanIntrospection.getAnnotationMetadata()
        );

        if (s.manifestRecorder.isRecording()) {
            s.manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                    modelMember::manifestMember, beanMethod.getReturnType().asArgument(), false,
                    FieldFetchers.isSingleResult(beanMethod));
        }
    }

    void bindTypeResolver(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                  TypeRuntimeWiring.Builder typeRuntimeWiringBuilder, Class<?> sourceClass,
                                  List<BeanDefinitionAndMethod> beanDefinitionAndMethods,
                                  List<InterfaceTypeDefinition> declaringInterfaces) {
        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        if (beanDefinitionAndMethods.size() > 1) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanDefinitionAndMethods));
        }

        if (!declaringInterfaces.isEmpty()) {
            s.ownInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition,
                    sourceClass, toMap(beanDefinitionAndMethods), null));
        }

        BeanDefinitionAndMethod beanDefinitionAndMethod = beanDefinitionAndMethods.get(0);

        s.recordResolverBinding(mappingContext, beanDefinitionAndMethod);

        ExecutableMethod<Object, ?> executable = beanDefinitionAndMethod.getExecutableMethod();

        fetchers.processExecutableMethod(
                executable,
                executable.getReturnType(),
                sourceClass,
                ResolverInstances.of(s.context, beanDefinitionAndMethod.getBeanDefinition()),
                typeRuntimeWiringBuilder,
                mappingContext,
                null
        );

        if (s.manifestRecorder.isRecording()) {
            s.manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_RESOLVER,
                    () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                    executable.getReturnType().asArgument(), false, FieldFetchers.isSingleResult(executable));
        }
    }

    MethodNotFoundException methodNotFound(String objectTypeName, Class<?> sourceClass, String fieldName,
                                                   TypeMappingContext mappingContext) {
        MethodNotFoundException exception =
                s.graphQLResolversRegistry.typeMethodNotFound(sourceClass, fieldName, mappingContext);
        Class<?> contractClass = s.contractTypes.get(objectTypeName);

        if (contractClass == null) {
            return s.hintClaimedFields(exception, objectTypeName, fieldName);
        }
        List<Class<?>> declaring = s.graphQLBeanIntrospectionRegistry.getImplementationClasses(contractClass).stream()
                .filter(it -> BeanIntrospector.SHARED.findIntrospection(it)
                        .map(introspection -> introspection.getProperty(fieldName).isPresent()).orElse(false))
                .toList();

        return s.hintClaimedFields(exception.forContract(contractClass, declaring), objectTypeName, fieldName);
    }
}
