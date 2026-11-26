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

import io.micronaut.graphql.tools.internal.wiring.interfaces.AbstractTypeWiring;
import io.micronaut.graphql.tools.internal.wiring.interfaces.DeclaringInterfaces;
import io.micronaut.graphql.tools.internal.wiring.interfaces.InterfaceField;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.scalars.ExtendedScalars;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.internal.checks.Ambiguities;
import io.micronaut.graphql.tools.internal.checks.AmbiguityLog;
import io.micronaut.graphql.tools.internal.checks.CheckPolicy;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.execution.FieldExecutors;
import io.micronaut.graphql.tools.internal.execution.GraphQLFieldFetcherDecorators;
import io.micronaut.graphql.tools.internal.input.InputTypeBinder;
import io.micronaut.graphql.tools.internal.input.ScalarMappings;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.BeanDefinitionAndMethod;
import io.micronaut.graphql.tools.internal.model.GraphQLBeanIntrospectionRegistry;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.internal.model.ModelMemberIndex;
import io.micronaut.graphql.tools.internal.model.RuntimeTypeModel;
import io.micronaut.graphql.tools.internal.resolver.ArgumentPlanner;
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.inject.ExecutableMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** The collaborators and the bindings that the binders of the runtime wiring share while one schema is bound. */
@Internal
final class WiringState {

    final BeanContext context;
    final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    final GraphQLResolversRegistry graphQLResolversRegistry;
    final TypeDefinitionRegistry typeDefinitionRegistry;
    final SchemaMappingDictionary schemaMappingDictionary;
    final GraphQLToolsConfiguration configuration;
    final CheckPolicy checkPolicy;
    final ScalarMappings scalarMappings;
    final BindingManifestRecorder manifestRecorder;
    final RuntimeWiring.Builder rootRuntimeWiringBuilder;
    final RuntimeTypeModel typeModel = new RuntimeTypeModel();
    final DeclaringInterfaces interfaceIndex;
    final InputTypeBinder inputTypeBinder;
    final ArgumentPlanner argumentPlanner;
    final FieldExecutors fieldExecutors;
    final GraphQLFieldFetcherDecorators fieldDecorators;
    final AbstractTypeWiring abstractTypeWiring;
    final AmbiguityLog ambiguityLog;
    final Set<String> extensionFields;

    final Map<String, Class<?>> processedTypes = new HashMap<>();
    final Map<String, Class<?>> contractTypes = new HashMap<>();
    final Map<String, String> fieldBindings = new HashMap<>();
    final Map<String, Set<String>> resolverBindings = new HashMap<>();

    // key: object type name, value: its wiring, registered once all its fields are bound (interface fields are bound
    // last, and graphql-java does not merge two wirings of one type the same way in every version)
    final Map<String, TypeRuntimeWiring.Builder> objectTypeWirings = new LinkedHashMap<>();

    final List<InterfaceField> unboundInterfaceFields = new ArrayList<>();
    final List<InterfaceField> interfaceResolverFields = new ArrayList<>();
    final List<InterfaceField> ownInterfaceFields = new ArrayList<>();
    final List<InterfaceField> deferredModelMemberFields = new ArrayList<>();
    final Map<ExecutableMethod<?, ?>, Set<String>> rootMethodBindings = new IdentityHashMap<>();
    final Map<String, List<Class<?>>> interfacesReadThroughImplementations = new TreeMap<>();
    final Map<BeanIntrospection<Object>, ModelMemberIndex> memberIndexes = new IdentityHashMap<>();
    final Set<String> boundUnreturnedInterfaces = new HashSet<>();

    Ambiguities ambiguities = Ambiguities.NONE;
    Map<String, String> claimedFields = Map.of();
    @Nullable
    String subscriptionTypeName;

    WiringState(BeanContext context, WiringComponents components, GraphQLResolversRegistry graphQLResolversRegistry,
                TypeDefinitionRegistry typeDefinitionRegistry, SchemaMappingDictionary schemaMappingDictionary,
                GraphQLToolsConfiguration configuration, Set<String> extensionFields,
                InputTypeBinder.Callbacks inputCallbacks) {
        this.context = context;
        this.graphQLBeanIntrospectionRegistry = components.introspections();
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.schemaMappingDictionary = schemaMappingDictionary;
        this.configuration = configuration;
        this.checkPolicy = components.checkPolicy();
        this.manifestRecorder = components.manifestRecorder();
        this.scalarMappings = components.scalarMappings();
        this.extensionFields = extensionFields;
        this.interfaceIndex = new DeclaringInterfaces(typeDefinitionRegistry);
        this.rootRuntimeWiringBuilder = RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.GraphQLShort)
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.GraphQLBigInteger);
        scalarMappings.all().forEach(it -> rootRuntimeWiringBuilder.scalar(it.getScalarType()));
        this.inputTypeBinder = new InputTypeBinder(typeDefinitionRegistry, scalarMappings, configuration,
                checkPolicy, manifestRecorder, typeModel, context, inputCallbacks);
        this.argumentPlanner = new ArgumentPlanner(inputTypeBinder, typeModel, context);
        this.fieldExecutors = components.fieldExecutors();
        this.fieldDecorators = components.fieldDecorators();
        this.ambiguityLog = new AmbiguityLog(configuration.getPrecedence(), manifestRecorder);
        this.abstractTypeWiring = new AbstractTypeWiring(typeDefinitionRegistry, schemaMappingDictionary,
                graphQLBeanIntrospectionRegistry, manifestRecorder, rootRuntimeWiringBuilder,
                configuration.getTypeClassConvention());
    }

    static <K, V> Map<K, V> snapshot(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    static Map<String, Set<String>> immutable(Map<String, Set<String>> bindings) {
        Map<String, Set<String>> copy = new TreeMap<>();
        bindings.forEach((method, coordinates) -> copy.put(method, Collections.unmodifiableSet(new TreeSet<>(coordinates))));
        return Collections.unmodifiableMap(copy);
    }

    void recordResolverBinding(TypeMappingContext mappingContext,
                                       BeanDefinitionAndMethod beanDefinitionAndMethod) {
        String coordinate = mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField();
        String method = GraphQLResolversRegistry.describeMethod(
                beanDefinitionAndMethod.getBeanDefinition(), beanDefinitionAndMethod.getExecutableMethod());

        fieldBindings.put(coordinate, "resolver " + method);
        resolverBindings.computeIfAbsent(method, it -> new TreeSet<>()).add(coordinate);
    }

    boolean isClaimed(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition) {
        String customizer = claimedFields.get(objectTypeDefinition.getName() + "." + fieldDefinition.getName());
        if (customizer == null) {
            return false;
        }
        manifestRecorder.claimed(objectTypeDefinition, fieldDefinition, customizer);
        return true;
    }

    MethodNotFoundException hintClaimedFields(MethodNotFoundException exception, String objectTypeName,
                                                      String fieldName) {
        return extensionFields.contains(objectTypeName + "." + fieldName) ? exception.forExtensionField() : exception;
    }
}
