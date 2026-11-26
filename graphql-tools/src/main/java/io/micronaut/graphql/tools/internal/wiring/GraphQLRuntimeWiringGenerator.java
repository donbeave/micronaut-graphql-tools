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
import io.micronaut.graphql.tools.internal.wiring.interfaces.InterfaceFieldBinder;
import io.micronaut.graphql.tools.internal.wiring.interfaces.InterfaceResolverMethod;
import graphql.language.ObjectTypeDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.customizer.GraphQLRuntimeWiringCustomizer;
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import io.micronaut.graphql.tools.internal.checks.CheckPolicy;
import io.micronaut.graphql.tools.internal.checks.DictionaryValidator;
import io.micronaut.graphql.tools.internal.checks.SchemaCoordinateValidator;
import io.micronaut.graphql.tools.internal.checks.UnreachableTypeChecks;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.RootResolverNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.configuration.SchemaDefinitionNotProvidedException;
import io.micronaut.graphql.tools.internal.input.InputTypeBinder;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.internal.resolver.ResolverInstances;
import io.micronaut.inject.ExecutableMethod;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;

/** Generates the graphql-java runtime wiring: binds each schema type and field to a resolver, data fetcher or type resolver. */
@Internal
public final class GraphQLRuntimeWiringGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final WiringState s;
    private final BindingWalk walk;
    private final TypeBinder types;
    private final FieldFetchers fetchers;
    private final ObjectFieldBinder fields;
    private final RootFieldBinder roots;
    private final ResolverDirectives directives;
    private final StrictModeFindings strict;
    private final SchemaCoordinateValidator coordinateValidator;
    private final DictionaryValidator dictionaryValidator;
    private final UnreachableTypeChecks unreachableTypeChecks;

    /**
     * @param context                 the bean context that serves the resolver beans, the converters and the executors
     * @param components              the collaborators that are built once for the schema
     * @param graphQLResolversRegistry the resolvers of the application
     * @param typeDefinitionRegistry  the schema
     * @param schemaMappingDictionary the type dictionary
     * @param configuration           the library configuration
     * @param extensionFields         the coordinates of the fields that a type extension adds
     */
    public GraphQLRuntimeWiringGenerator(BeanContext context,
                                         WiringComponents components,
                                         GraphQLResolversRegistry graphQLResolversRegistry,
                                         TypeDefinitionRegistry typeDefinitionRegistry,
                                         SchemaMappingDictionary schemaMappingDictionary,
                                         GraphQLToolsConfiguration configuration,
                                         Set<String> extensionFields) {
        requireNonNull("context", context);
        requireNonNull("components", components);
        requireNonNull("graphQLResolversRegistry", graphQLResolversRegistry);
        requireNonNull("typeDefinitionRegistry", typeDefinitionRegistry);
        requireNonNull("schemaParserDictionary", schemaMappingDictionary);
        requireNonNull("configuration", configuration);

        this.s = new WiringState(context, components, graphQLResolversRegistry, typeDefinitionRegistry,
                schemaMappingDictionary, configuration, extensionFields, new InputCallbacks());
        this.coordinateValidator = new SchemaCoordinateValidator(typeDefinitionRegistry, graphQLResolversRegistry,
                s.graphQLBeanIntrospectionRegistry, s.checkPolicy);
        this.dictionaryValidator = new DictionaryValidator(typeDefinitionRegistry, schemaMappingDictionary);
        this.unreachableTypeChecks = new UnreachableTypeChecks(typeDefinitionRegistry, configuration, s.checkPolicy,
                s.manifestRecorder);
        this.walk = new BindingWalk();
        this.types = new TypeBinder(s, walk, this::processField);
        this.fetchers = new FieldFetchers(s, types);
        this.directives = new ResolverDirectives(s);
        this.fields = new ObjectFieldBinder(s, types, fetchers, directives);
        this.roots = new RootFieldBinder(s, walk, fetchers);
        this.strict = new StrictModeFindings(s);
    }

    private void processField(FieldDefinition fieldDefinition, ObjectTypeDefinition objectTypeDefinition,
                              TypeRuntimeWiring.Builder typeRuntimeWiringBuilder, BeanIntrospection<Object> beanIntrospection) {
        fields.processFieldDefinition(fieldDefinition, objectTypeDefinition, typeRuntimeWiringBuilder, beanIntrospection);
    }

    public BindingManifestRecorder getManifestRecorder() {
        return s.manifestRecorder;
    }

    public CheckPolicy getCheckPolicy() {
        return s.checkPolicy;
    }

    public RuntimeWiring generate(List<GraphQLRuntimeWiringCustomizer> customizers) {
        long start = System.nanoTime();

        try {
            SchemaDefinition schemaDefinition = s.typeDefinitionRegistry.schemaDefinition()
                    .orElseThrow(SchemaDefinitionNotProvidedException::new);

            s.graphQLResolversRegistry.verifyRootMarkersRecorded();

            if (!s.graphQLResolversRegistry.hasRootResolvers()) {
                throw new RootResolverNotFoundException();
            }

            s.graphQLResolversRegistry.verifyTypeResolverContracts();

            coordinateValidator.validateSchemaCoordinates(schemaDefinition);

            dictionaryValidator.validate();

            directives.validate();

            s.claimedFields = GraphQLRuntimeWiringCustomizers.claims(customizers, s.typeDefinitionRegistry);

            s.subscriptionTypeName = SchemaModel.load(s.typeDefinitionRegistry)
                    .flatMap(SchemaModel::subscriptionType)
                    .orElse(null);

            for (OperationTypeDefinition operationTypeDefinition : schemaDefinition.getOperationTypeDefinitions()) {
                roots.processOperationTypeDefinition(operationTypeDefinition);
            }

            bindInterfaceFields();

            walk.throwBindingFailures();

            s.inputTypeBinder.logNullablePrimitiveWarnings();
            s.inputTypeBinder.inputClassChecks().checkCollected(s.checkPolicy, s.manifestRecorder);

            if (!s.ambiguities.bindings().isEmpty()) {
                LOG.warn("{} is {}: fields that both a member of the Java type and a type resolver method can bind: {}",
                        GraphQLToolsConfiguration.PREFIX + ".precedence", PrecedenceRules.Setting.valueOf(s.configuration.getPrecedence().name()).token(),
                        s.ambiguities.bindings().entrySet().stream()
                                .map(it -> it.getKey() + " (" + it.getValue() + ")")
                                .collect(Collectors.joining("; ")));
            }

            s.objectTypeWirings.values().forEach(it -> s.rootRuntimeWiringBuilder.type(it.build()));

            Set<String> implementedInterfaces = s.abstractTypeWiring.wireInterfaces(WiringState.snapshot(s.processedTypes));

            s.abstractTypeWiring.checkAmbiguity();

            UnreachableTypeChecks.Reachability reachability = unreachableTypeChecks.processUnreachableTypes(
                    schemaDefinition, implementedInterfaces, WiringState.snapshot(s.processedTypes), s.rootRuntimeWiringBuilder);
            Set<String> unboundTypes = reachability.unbound();

            Optional<SchemaBindingException> coordinateFinding = coordinateValidator.verifySchemaCoordinateBindings(
                    unboundTypes, WiringState.snapshot(s.processedTypes), WiringState.snapshot(s.fieldBindings), WiringState.immutable(s.resolverBindings));

            Optional<SchemaBindingException> strictFinding = strict.checkStrictMode(unboundTypes);

            StrictModeFindings.throwFindings(Stream.of(Optional.ofNullable(reachability.finding()), coordinateFinding, strictFinding)
                    .flatMap(Optional::stream)
                    .toList());

            s.rootRuntimeWiringBuilder.wiringFactory(new DefaultWiringFactory(unboundTypes));

            GraphQLRuntimeWiringCustomizers.apply(s.rootRuntimeWiringBuilder, customizers, s.typeDefinitionRegistry, s.claimedFields);

            return s.rootRuntimeWiringBuilder.build();
        } finally {
            if (LOG.isInfoEnabled()) {
                long took = elapsedMillis(start);
                LOG.info("GraphQL runtime wiring generated in {}ms.", took);
            }
        }
    }

    private void bindInterfaceFields() {
        InterfaceFieldBinder.InterfaceBindings bindings = new InterfaceFieldBinder(s.interfaceIndex,
                s.graphQLResolversRegistry, Collections.unmodifiableMap(s.processedTypes), s.configuration.getPrecedence(),
                s.manifestRecorder, new InterfaceCallbacks()).bind();
        s.fieldBindings.putAll(bindings.fieldBindings());
        bindings.resolverBindings().forEach((method, coordinates) ->
                s.resolverBindings.computeIfAbsent(method, it -> new TreeSet<>()).addAll(coordinates));
        walk.addFailures(bindings.failures());
        s.boundUnreturnedInterfaces.addAll(bindings.boundUnreturned());
        s.ambiguities = s.ambiguityLog.snapshot().plus(bindings.ambiguities());
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private final class InputCallbacks implements InputTypeBinder.Callbacks {

        @Override
        public void processIfNotProcessed(TypeDefinition<?> typeDefinition, Class<?> targetClass,
                                          MappingContext mappingContext, Runnable runnable) {
            types.processIfNotProcessed(typeDefinition, targetClass, mappingContext,
                    runnable);
        }

        @Override
        public void processEnum(EnumTypeDefinition enumTypeDefinition, Class<?> targetClass, boolean input,
                                MappingContext mappingContext) {
            types.processEnumTypeDefinition(enumTypeDefinition, targetClass, input, mappingContext);
        }
    }

    private final class InterfaceCallbacks implements InterfaceFieldBinder.Callbacks {

        @Override
        public InterfaceFieldBinder.InterfaceFields takeDiscoveredFields() {
            return new InterfaceFieldBinder.InterfaceFields(drain(s.unboundInterfaceFields), drain(s.ownInterfaceFields),
                    drain(s.deferredModelMemberFields), drain(s.interfaceResolverFields));
        }

        private static List<InterfaceField> drain(List<InterfaceField> fields) {
            List<InterfaceField> taken = List.copyOf(fields);
            fields.clear();
            return taken;
        }

        @Override
        public void bindModelMember(InterfaceField field) {
            fields.bindModelMember(field.objectTypeDefinition(), field.fieldDefinition(),
                    s.objectTypeWirings.get(field.objectTypeDefinition().getName()), field.modelMember());
        }

        @Override
        public void wireResolver(InterfaceField field, InterfaceResolverMethod method) {
            ExecutableMethod<Object, ?> executable = method.method().getExecutableMethod();

            fetchers.processExecutableMethod(
                    executable,
                    executable.getReturnType(),
                    method.interfaceClass(),
                    ResolverInstances.of(s.context, method.method().getBeanDefinition()),
                    s.objectTypeWirings.get(field.objectTypeDefinition().getName()),
                    field.mappingContext(),
                    null
            );

            if (s.manifestRecorder.isRecording()) {
                s.manifestRecorder.field(field.objectTypeDefinition(), field.fieldDefinition(),
                        BindingManifestRecorder.KIND_RESOLVER,
                        () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                        executable.getReturnType().asArgument(), false, FieldFetchers.isSingleResult(executable));
            }
        }

        @Override
        public MethodNotFoundException methodNotFound(String objectTypeName, Class<?> sourceClass, String fieldName,
                                                      TypeMappingContext mappingContext) {
            return fields.methodNotFound(objectTypeName, sourceClass, fieldName,
                    mappingContext);
        }

        @Override
        public Class<?> findInterfaceClass(InterfaceTypeDefinition interfaceType, TypeMappingContext mappingContext) {
            return s.abstractTypeWiring.findInterfaceClass(interfaceType, mappingContext);
        }

        @Override
        public String resolverDirective(FieldDefinition fieldDefinition) {
            return directives.find(fieldDefinition);
        }
    }
}
