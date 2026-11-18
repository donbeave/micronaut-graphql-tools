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

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.scalars.ExtendedScalars;
import graphql.schema.idl.MapEnumValuesProvider;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.ReturnType;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration.Precedence;
import io.micronaut.graphql.tools.binding.internal.rules.EnumTypeRules;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.exceptions.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException;
import io.micronaut.graphql.tools.exceptions.BindingFailuresException;
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException;
import io.micronaut.graphql.tools.exceptions.InterfaceTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.exceptions.MappingConflictException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.exceptions.MissingEnumValuesException;
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.exceptions.ResolverRequiredException;
import io.micronaut.graphql.tools.exceptions.RootResolverNotFoundException;
import io.micronaut.graphql.tools.exceptions.SchemaDefinitionNotProvidedException;
import io.micronaut.graphql.tools.exceptions.StrictModeException;
import io.micronaut.graphql.tools.schema.ArgumentValueModule;
import io.micronaut.graphql.tools.schema.DefaultWiringFactory;
import io.micronaut.graphql.tools.schema.InputValueConverter;
import io.micronaut.graphql.tools.schema.MicronautExecutableMethodDataFetcher;
import io.micronaut.graphql.tools.schema.MicronautIntrospectionDataFetcher;
import io.micronaut.graphql.tools.schema.MicronautPropertyDataFetcher;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jackson.modules.BeanIntrospectionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;
import static io.micronaut.graphql.tools.GraphQLUtils.getType;
import static io.micronaut.graphql.tools.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.GraphQLUtils.requireTypeName;
import static io.micronaut.graphql.tools.GraphQLUtils.unsupportedTypeDefinition;
import static io.micronaut.graphql.tools.GraphQLUtils.unwrapNonNullType;
import static io.micronaut.graphql.tools.MicronautUtils.getExecutableMethodFullName;
import static io.micronaut.graphql.tools.MicronautUtils.getPropertyMethodName;
import static io.micronaut.graphql.tools.MicronautUtils.toMap;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.getSupportedClasses;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.isGraphQlBuiltInType;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.isJavaBuiltInClass;

@Internal
final class GraphQLRuntimeWiringGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final ApplicationContext applicationContext;
    private final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final SchemaMappingDictionary schemaMappingDictionary;
    private final InputValueConverter inputValueConverter;
    private final Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans = new ConcurrentHashMap<>();
    private final InputClassChecks inputClassChecks;
    private final GraphQLToolsConfiguration configuration;
    private final ScalarMappings scalarMappings;
    private final RuntimeWiring.Builder rootRuntimeWiringBuilder;

    private final Map<String, Class<?>> processedTypes = new HashMap<>();

    private final Map<String, Class<?>> contractTypes = new HashMap<>();

    private final Map<String, String> fieldBindings = new HashMap<>();

    private final Map<String, Set<String>> resolverBindings = new HashMap<>();

    // key: object type name, value: its wiring, registered once all its fields are bound (interface fields are bound
    // last, and graphql-java does not merge two wirings of one type the same way in every version)
    private final Map<String, TypeRuntimeWiring.Builder> objectTypeWirings = new LinkedHashMap<>();

    private final List<InterfaceField> unboundInterfaceFields = new ArrayList<>();

    private final List<InterfaceField> interfaceResolverFields = new ArrayList<>();

    private final List<InterfaceField> ownInterfaceFields = new ArrayList<>();

    private final List<InterfaceField> deferredModelMemberFields = new ArrayList<>();

    private final Map<ExecutableMethod<?, ?>, Set<String>> rootMethodBindings = new IdentityHashMap<>();

    private final Map<String, List<Class<?>>> interfacesReadThroughImplementations = new TreeMap<>();

    private final List<AbstractMappingException> bindingFailures = new ArrayList<>();

    private AmbiguityLog ambiguityLog;

    private Ambiguities ambiguities = Ambiguities.NONE;

    private Map<String, String> claimedFields = Map.of();

    private final Set<String> extensionFields;

    private final BindingManifestRecorder manifestRecorder;

    private final SchemaCoordinateValidator coordinateValidator;

    private final DictionaryValidator dictionaryValidator;

    private final UnreachableTypeChecks unreachableTypeChecks;

    private final RuntimeTypeModel typeModel = new RuntimeTypeModel();

    private final InputTypeBinder inputTypeBinder;

    private final ArgumentPlanner argumentPlanner;

    private final AbstractTypeWiring abstractTypeWiring;

    @Nullable
    private String subscriptionTypeName;

    GraphQLRuntimeWiringGenerator(ApplicationContext applicationContext,
                                  GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry,
                                  GraphQLResolversRegistry graphQLResolversRegistry,
                                  TypeDefinitionRegistry typeDefinitionRegistry,
                                  SchemaMappingDictionary schemaMappingDictionary,
                                  GraphQLToolsConfiguration configuration,
                                  Set<String> extensionFields) {
        requireNonNull("applicationContext", applicationContext);
        requireNonNull("graphQLBeanIntrospectionRegistry", graphQLBeanIntrospectionRegistry);
        requireNonNull("graphQLResolversRegistry", graphQLResolversRegistry);
        requireNonNull("typeDefinitionRegistry", typeDefinitionRegistry);
        requireNonNull("schemaParserDictionary", schemaMappingDictionary);
        requireNonNull("configuration", configuration);

        this.applicationContext = applicationContext;
        this.graphQLBeanIntrospectionRegistry = graphQLBeanIntrospectionRegistry;
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.schemaMappingDictionary = schemaMappingDictionary;
        this.configuration = configuration;
        this.extensionFields = extensionFields;
        this.manifestRecorder = new BindingManifestRecorder(configuration.getManifest().isRecorded());
        this.coordinateValidator = new SchemaCoordinateValidator(typeDefinitionRegistry, graphQLResolversRegistry,
                graphQLBeanIntrospectionRegistry, configuration);
        this.dictionaryValidator = new DictionaryValidator(typeDefinitionRegistry, schemaMappingDictionary);
        this.unreachableTypeChecks = new UnreachableTypeChecks(typeDefinitionRegistry, configuration, manifestRecorder);

        this.scalarMappings = new ScalarMappings(applicationContext.getBeansOfType(GraphQLScalarMapping.class),
                manifestRecorder, applicationContext);

        this.inputValueConverter = new InputValueConverter(
                createInputObjectMapper(applicationContext, scalarMappings.all(), configuration),
                omissionPlans
        );
        this.inputClassChecks = new InputClassChecks(inputValueConverter, omissionPlans);
        this.rootRuntimeWiringBuilder = RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.GraphQLShort)
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.GraphQLBigInteger);
        scalarMappings.all().forEach(it -> rootRuntimeWiringBuilder.scalar(it.getScalarType()));
        this.inputTypeBinder = new InputTypeBinder(typeDefinitionRegistry, scalarMappings, configuration,
                manifestRecorder, inputClassChecks, omissionPlans, typeModel, new InputCallbacks());
        this.argumentPlanner = new ArgumentPlanner(inputTypeBinder, typeModel);
        this.ambiguityLog = new AmbiguityLog(configuration.getPrecedence(), manifestRecorder);
        this.abstractTypeWiring = new AbstractTypeWiring(typeDefinitionRegistry, schemaMappingDictionary,
                graphQLBeanIntrospectionRegistry, manifestRecorder, rootRuntimeWiringBuilder);
    }

    private static ObjectMapper createInputObjectMapper(ApplicationContext applicationContext,
                                                        Collection<GraphQLScalarMapping> scalarMappings,
                                                        GraphQLToolsConfiguration configuration) {
        ObjectMapper objectMapper = applicationContext
                .findBean(ObjectMapper.class, Qualifiers.byName(GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER))
                .map(ObjectMapper::copy)
                .orElseGet(() -> new ObjectMapper().registerModule(new BeanIntrospectionModule()));
        objectMapper.registerModule(new ScalarValuesModule(scalarMappings));
        if (configuration.getInput().isDetectOmission()) {
            objectMapper.registerModule(new ArgumentValueModule());
        }
        return objectMapper;
    }

    BindingManifestRecorder getManifestRecorder() {
        return manifestRecorder;
    }

    RuntimeWiring generate(List<RuntimeWiringCustomizer> customizers) {
        long start = System.nanoTime();

        try {
            SchemaDefinition schemaDefinition = typeDefinitionRegistry.schemaDefinition()
                    .orElseThrow(SchemaDefinitionNotProvidedException::new);

            if (!graphQLResolversRegistry.hasRootResolvers()) {
                throw new RootResolverNotFoundException();
            }

            graphQLResolversRegistry.verifyTypeResolverContracts();

            coordinateValidator.validateSchemaCoordinates(schemaDefinition);

            dictionaryValidator.validate();

            validateResolverDirectives();

            claimedFields = RuntimeWiringCustomizers.claims(customizers, typeDefinitionRegistry);

            subscriptionTypeName = SchemaModel.load(typeDefinitionRegistry)
                    .flatMap(SchemaModel::subscriptionType)
                    .orElse(null);

            for (OperationTypeDefinition operationTypeDefinition : schemaDefinition.getOperationTypeDefinitions()) {
                processOperationTypeDefinition(operationTypeDefinition);
            }

            bindInterfaceFields();

            throwBindingFailures();

            GraphQLToolsConfiguration.UnmappedProperties unmappedProperties =
                    configuration.getInput().getUnmappedProperties();
            inputClassChecks.checkCollected(unmappedProperties == null
                    ? configuration.isStrict()
                    : unmappedProperties == GraphQLToolsConfiguration.UnmappedProperties.FAIL, manifestRecorder);

            if (!ambiguities.bindings().isEmpty()) {
                LOG.warn("{} is {}: fields that both a member of the Java type and a type resolver method can bind: {}",
                        GraphQLToolsConfiguration.PREFIX + ".precedence", configuration.getPrecedence(),
                        ambiguities.bindings().entrySet().stream()
                                .map(it -> it.getKey() + " (" + it.getValue() + ")")
                                .collect(Collectors.joining("; ")));
            }

            objectTypeWirings.values().forEach(it -> rootRuntimeWiringBuilder.type(it.build()));

            Set<String> implementedInterfaces = abstractTypeWiring.wireInterfaces(processedTypes);

            abstractTypeWiring.checkAmbiguity();

            UnreachableTypeChecks.Reachability reachability = unreachableTypeChecks.processUnreachableTypes(
                    schemaDefinition, implementedInterfaces, snapshot(processedTypes), rootRuntimeWiringBuilder);
            Set<String> unboundTypes = reachability.unbound();

            Optional<RuntimeException> coordinateFinding = coordinateValidator.verifySchemaCoordinateBindings(
                    unboundTypes, snapshot(processedTypes), snapshot(fieldBindings), immutable(resolverBindings));

            Optional<RuntimeException> strictFinding = checkStrictMode(unboundTypes);

            throwFindings(Stream.of(Optional.ofNullable(reachability.finding()), coordinateFinding, strictFinding)
                    .flatMap(Optional::stream)
                    .toList());

            rootRuntimeWiringBuilder.wiringFactory(new DefaultWiringFactory(unboundTypes));

            RuntimeWiringCustomizers.apply(rootRuntimeWiringBuilder, customizers, typeDefinitionRegistry, claimedFields);

            return rootRuntimeWiringBuilder.build();
        } finally {
            if (LOG.isInfoEnabled()) {
                long took = elapsedMillis(start);
                LOG.info("GraphQL runtime wiring generated in {}ms.", took);
            }
        }
    }

    private static <K, V> Map<K, V> snapshot(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private static Map<String, Set<String>> immutable(Map<String, Set<String>> bindings) {
        Map<String, Set<String>> copy = new TreeMap<>();
        bindings.forEach((method, coordinates) -> copy.put(method, Collections.unmodifiableSet(new TreeSet<>(coordinates))));
        return Collections.unmodifiableMap(copy);
    }

    private Optional<RuntimeException> checkStrictMode(Set<String> unboundTypes) {
        Set<String> allowedTypes = new HashSet<>(configuration.getAllowUnreachable());
        if (configuration.getUnreachableTypes() == GraphQLToolsConfiguration.UnreachableTypes.IGNORE) {
            allowedTypes.addAll(unboundTypes);
        }

        if (!configuration.isStrict() && !manifestRecorder.isRecording()) {
            return Optional.empty();
        }

        StrictModeChecks checks = new StrictModeChecks(manifestRecorder);
        Set<String> accountedMethods = new HashSet<>(resolverBindings.keySet());
        accountedMethods.addAll(ambiguities.shadowedResolverMethods());
        checks.unboundResolverMethods(graphQLResolversRegistry.getResolverMethods(), accountedMethods);
        checks.ignoredResolverMethods(graphQLResolversRegistry.getIgnoredMethods());
        checks.rootMethodsOfSeveralOperationTypes(rootMethodBindings);
        checks.unboundEnumConstants(snapshot(processedTypes), typeDefinitionRegistry, typeModel);
        checks.unusedDictionaryEntries(schemaMappingDictionary.getTypes(), processedTypes.keySet(), allowedTypes);
        checks.interfacesReadThroughImplementations(interfacesReadThroughImplementations);

        return configuration.isStrict() && !checks.getProblems().isEmpty()
                ? Optional.of(new StrictModeException(checks.getProblems()))
                : Optional.empty();
    }

    private static void throwFindings(List<RuntimeException> findings) {
        if (findings.size() == 1) {
            throw findings.get(0);
        }
        if (!findings.isEmpty()) {
            throw new BindingFailuresException(findings);
        }
    }

    private void recordResolverBinding(TypeMappingContext mappingContext,
                                       BeanDefinitionAndMethod beanDefinitionAndMethod) {
        String coordinate = mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField();
        String method = GraphQLResolversRegistry.describeMethod(
                beanDefinitionAndMethod.getBeanDefinition(), beanDefinitionAndMethod.getExecutableMethod());

        fieldBindings.put(coordinate, "resolver " + method);
        resolverBindings.computeIfAbsent(method, it -> new TreeSet<>()).add(coordinate);
    }

    void processExecutableMethod(Executable<Object, ?> executable, ReturnType<?> returnType,
                                 @Nullable Class<?> sourceClass, @Nullable Object instance,
                                 TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                 TypeMappingContext mappingContext) {
        mappingContext = TypeMappingContext.forField(
                mappingContext,
                executable.getDeclaringType(),
                getExecutableMethodFullName(executable)
        );

        List<ArgumentDefinition> argumentDefinitions = argumentPlanner.plan(executable, sourceClass,
                mappingContext);

        typeRuntimeWiringBuilder.dataFetcher(
                mappingContext.getFieldDefinition().getName(),
                new MicronautExecutableMethodDataFetcher(inputValueConverter, executable, argumentDefinitions, instance)
        );

        processFieldReturnType(returnType.asArgument(), mappingContext.getFieldDefinition().getType(),
                mappingContext);
    }

    private void processFieldReturnType(Argument<?> argument, Type<?> graphQlType, TypeMappingContext mappingContext) {
        processFieldReturnType(argument, graphQlType, mappingContext, true);
    }

    private void processFieldReturnType(Argument<?> argument, Type<?> graphQlType, TypeMappingContext mappingContext,
                                        boolean fieldLevel) {
        boolean subscription = mappingContext.getGraphQlObjectType().equals(subscriptionTypeName);
        argument = ReturnTypes.unwrap(argument, graphQlType, fieldLevel, subscription, mappingContext);
        graphQlType = unwrapNonNullType(graphQlType);

        Class<?> returnClass = argument.getType();

        if (graphQlType instanceof ListType listType) {
            if (!(Iterable.class.isAssignableFrom(returnClass) || Iterator.class.isAssignableFrom(returnClass))) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.ITERABLE,
                        mappingContext,
                        returnClass,
                        getSupportedClasses(listType)
                );
            }

            Type<?> listFieldType = (listType).getType();
            Argument<?> listArgument = argument.getFirstTypeVariable().get();

            processFieldReturnType(listArgument, listFieldType, mappingContext, false);
            return;
        }

        TypeName typeName = requireTypeName(graphQlType);

        if (isGraphQlBuiltInType(typeName)) {
            manifestRecorder.scalar(typeName.getName());
            Set<Class<?>> supportedClasses = getSupportedClasses(getTypeName(graphQlType));

            if (!supportedClasses.contains(returnClass)) {
                throw IncorrectClassMappingException.forField(mappingContext, returnClass, supportedClasses);
            }
        } else {
            TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(typeName.getName()).get();

            if (typeDefinition instanceof EnumTypeDefinition enumTypeDefinition) {
                processEnumTypeDefinition(enumTypeDefinition, returnClass, false, mappingContext);
            } else if (typeDefinition instanceof UnionTypeDefinition unionTypeDefinition) {
                processUnionTypeDefinition(unionTypeDefinition, returnClass, mappingContext);
            } else if (typeDefinition instanceof ObjectTypeDefinition objectTypeDefinition) {
                processObjectTypeDefinition(objectTypeDefinition, returnClass, mappingContext);
            } else if (typeDefinition instanceof InterfaceTypeDefinition interfaceTypeDefinition) {
                processInterfaceTypeDefinition(interfaceTypeDefinition, returnClass, mappingContext);
            } else if (typeDefinition instanceof ScalarTypeDefinition scalarTypeDefinition) {
                GraphQLScalarMapping scalarMapping = scalarMappings.require(scalarTypeDefinition, mappingContext);

                if (scalarMapping.getOutputClasses().isEmpty()) {
                    throw IncorrectClassMappingException.forScalarWithoutClasses(mappingContext, returnClass,
                            scalarMapping.getName(), false);
                }
                if (!scalarMapping.isOutputClass(returnClass)) {
                    throw IncorrectClassMappingException.forField(mappingContext, returnClass,
                            scalarMapping.getOutputClasses());
                }
            } else {
                throw unsupportedTypeDefinition(typeDefinition);
            }
        }
    }

    private void processOperationTypeDefinition(OperationTypeDefinition operationTypeDefinition) {
        ObjectTypeDefinition objectTypeDefinition = typeDefinitionRegistry
                .getType(operationTypeDefinition.getTypeName(), ObjectTypeDefinition.class).get();

        manifestRecorder.type(objectTypeDefinition.getName(), "object", null);

        rootRuntimeWiringBuilder.type(operationTypeDefinition.getTypeName().getName(), typeRuntimeWiringBuilder -> {
            for (FieldDefinition fieldDefinition : objectTypeDefinition.getFieldDefinitions()) {
                collectFailure(TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName()),
                        () -> processRootFieldDefinition(objectTypeDefinition, fieldDefinition,
                        typeRuntimeWiringBuilder));
            }

            return typeRuntimeWiringBuilder;
        });
    }

    private void processRootFieldDefinition(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                            TypeRuntimeWiring.Builder typeRuntimeWiringBuilder) {
        if (isClaimed(objectTypeDefinition, fieldDefinition)) {
            return;
        }

        TypeMappingContext mappingContext =
                TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        List<BeanDefinitionAndMethod> beanDefinitionAndMethods;
        try {
            beanDefinitionAndMethods = graphQLResolversRegistry.getRootExecutableMethods(
                    objectTypeDefinition.getName(), fieldDefinition.getName(), mappingContext);
        } catch (MethodNotFoundException e) {
            throw hintClaimedFields(e, objectTypeDefinition.getName(), fieldDefinition.getName());
        }

        if (beanDefinitionAndMethods.size() > 1) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanDefinitionAndMethods));
        }

        BeanDefinitionAndMethod beanDefinitionAndMethod = beanDefinitionAndMethods.get(0);

        recordResolverBinding(mappingContext, beanDefinitionAndMethod);

        ExecutableMethod<Object, ?> executable = beanDefinitionAndMethod.getExecutableMethod();

        processExecutableMethod(
                executable,
                executable.getReturnType(),
                null,
                ResolverInstances.of(applicationContext, beanDefinitionAndMethod.getBeanDefinition()),
                typeRuntimeWiringBuilder,
                mappingContext
        );

        rootMethodBindings.computeIfAbsent(executable, it -> new TreeSet<>())
                .add(objectTypeDefinition.getName() + "." + fieldDefinition.getName());

        if (manifestRecorder.isRecording()) {
            manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_ROOT,
                    () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                    executable.getReturnType().asArgument(),
                    objectTypeDefinition.getName().equals(subscriptionTypeName));
        }
    }

    private boolean isClaimed(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition) {
        String customizer = claimedFields.get(objectTypeDefinition.getName() + "." + fieldDefinition.getName());
        if (customizer == null) {
            return false;
        }
        manifestRecorder.claimed(objectTypeDefinition, fieldDefinition, customizer);
        return true;
    }

    private MethodNotFoundException hintClaimedFields(MethodNotFoundException exception, String objectTypeName,
                                                      String fieldName) {
        return extensionFields.contains(objectTypeName + "." + fieldName) ? exception.forExtensionField() : exception;
    }

    private void processEnumTypeDefinition(EnumTypeDefinition enumTypeDefinition, Class<?> targetClass, boolean input,
                                           MappingContext mappingContext) {
        processIfNotProcessed(enumTypeDefinition, targetClass, mappingContext, () -> {
            if (!targetClass.isEnum()) {
                if (input) {
                    throw IncorrectClassMappingException.forArgument(
                            IncorrectClassMappingException.MappingType.DETECT_TYPE,
                            IncorrectClassMappingException.MappingType.ENUM,
                            mappingContext,
                            targetClass,
                            null
                    );
                } else {
                    throw IncorrectClassMappingException.forField(
                            IncorrectClassMappingException.MappingType.DETECT_TYPE,
                            IncorrectClassMappingException.MappingType.ENUM,
                            mappingContext,
                            targetClass,
                            null
                    );
                }
            }

            List<String> expectedValues = enumTypeDefinition.getEnumValueDefinitions().stream()
                    .map(EnumValueDefinition::getName)
                    .distinct()
                    .sorted()
                    .toList();

            EnumBeanIntrospection<?> introspection = EnumIntrospections.find(targetClass).orElseThrow(
                    () -> new ClassNotIntrospectedException(mappingContext, targetClass, Introspected.class));
            Map<String, Object> constants = EnumIntrospections.constantsByName(introspection);

            List<String> missingValues = EnumTypeRules.check(expectedValues, typeModel.of(targetClass))
                    .missingSchemaValues();

            if (!missingValues.isEmpty()) {
                throw new MissingEnumValuesException(mappingContext, targetClass, missingValues);
            }

            Map<String, Object> values = new HashMap<>();
            expectedValues.forEach(it -> values.put(it, constants.get(it)));
            rootRuntimeWiringBuilder.type(enumTypeDefinition.getName(),
                    typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder.enumValues(new MapEnumValuesProvider(values)));

            manifestRecorder.type(enumTypeDefinition.getName(), "enum", targetClass);
            expectedValues.forEach(it -> manifestRecorder.enumValue(enumTypeDefinition.getName(), it, targetClass));
        });
    }

    private void processUnionTypeDefinition(UnionTypeDefinition unionTypeDefinition, Class<?> targetClass,
                                            TypeMappingContext mappingContext) {
        processIfNotProcessed(unionTypeDefinition, targetClass, mappingContext, () -> {
            if (!targetClass.isInterface()) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.INTERFACE,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            manifestRecorder.type(unionTypeDefinition.getName(), "union", targetClass);

            abstractTypeWiring.wireUnion(unionTypeDefinition, targetClass, mappingContext,
                    this::processObjectTypeDefinition);
        });
    }

    private void processInterfaceTypeDefinition(InterfaceTypeDefinition interfaceTypeDefinition, Class<?> targetClass,
                                                TypeMappingContext mappingContext) {
        processIfNotProcessed(interfaceTypeDefinition, targetClass, mappingContext, () -> {
            if (!targetClass.isInterface()) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.INTERFACE,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            manifestRecorder.type(interfaceTypeDefinition.getName(), "interface", targetClass);

            for (ObjectTypeDefinition implementation
                    : abstractTypeWiring.getImplementations(interfaceTypeDefinition)) {
                Class<?> clazz = Optional
                        .ofNullable(schemaMappingDictionary.getTypes().get(implementation.getName()))
                        .orElseThrow(() -> new InterfaceTypeMappingNotProvidedException(
                                mappingContext, implementation.getName(), interfaceTypeDefinition.getName()
                        ));

                AbstractTypeWiring.checkMemberClass("interface", interfaceTypeDefinition.getName(), targetClass,
                        implementation.getName(), clazz, mappingContext);

                processObjectTypeDefinition(implementation, clazz, mappingContext);
            }
        });
    }

    private void processObjectTypeDefinition(ObjectTypeDefinition objectTypeDefinition, Class<?> targetClass,
                                             TypeMappingContext mappingContext) {
        processIfNotProcessed(objectTypeDefinition, targetClass, mappingContext, () -> {
            Optional.ofNullable(schemaMappingDictionary.getTypes().get(objectTypeDefinition.getName()))
                    .ifPresent(registeredClass -> {
                        if (!bindsAlike(targetClass, registeredClass, mappingContext)) {
                            throw new MappingConflictException(
                                    mappingContext,
                                    getType(objectTypeDefinition),
                                    objectTypeDefinition.getName(),
                                    targetClass,
                                    registeredClass
                            );
                        }
                    });

            if (isJavaBuiltInClass(targetClass)) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            BeanIntrospection<Object> beanIntrospection =
                    graphQLBeanIntrospectionRegistry.getGraphQlTypeBeanIntrospection(mappingContext, targetClass);
            if (beanIntrospection.getBeanType().isInterface()) {
                contractTypes.put(objectTypeDefinition.getName(), beanIntrospection.getBeanType());
            } else if (targetClass.isInterface()) {
                interfacesReadThroughImplementations.put(objectTypeDefinition.getName(),
                        List.of(targetClass, beanIntrospection.getBeanType()));
            }
            manifestRecorder.type(objectTypeDefinition.getName(), "object", beanIntrospection.getBeanType());

            TypeRuntimeWiring.Builder typeRuntimeWiringBuilder = TypeRuntimeWiring
                    .newTypeWiring(objectTypeDefinition.getName())
                    .defaultDataFetcher(new MicronautIntrospectionDataFetcher(beanIntrospection));
            objectTypeWirings.put(objectTypeDefinition.getName(), typeRuntimeWiringBuilder);

            for (FieldDefinition fieldDefinition : objectTypeDefinition.getFieldDefinitions()) {
                collectFailure(TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName()),
                        () -> processFieldDefinition(
                                fieldDefinition, objectTypeDefinition, typeRuntimeWiringBuilder, beanIntrospection));
            }
        });
    }

    private boolean isIgnored(AnnotationMetadataProvider member, String name) {
        if (!member.getAnnotationMetadata().hasAnnotation(GraphQLIgnore.class)) {
            return false;
        }
        manifestRecorder.ignored(name);
        return true;
    }

    private void processFieldDefinition(FieldDefinition fieldDefinition, ObjectTypeDefinition objectTypeDefinition,
                                        TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                        BeanIntrospection<Object> beanIntrospection) {
        if (isClaimed(objectTypeDefinition, fieldDefinition)) {
            return;
        }

        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        Optional<BeanProperty<Object, Object>> beanProperty =
                beanIntrospection.getProperty(fieldDefinition.getName())
                        .filter(it -> !isIgnored(it, BindingManifestRecorder.propertyMember(it)));
        List<BeanMethod<Object, ?>> beanMethods = beanIntrospection.getBeanMethods().stream()
                .filter(it -> it.getName().equals(fieldDefinition.getName()))
                .filter(it -> !isIgnored(it, BindingManifestRecorder.introspectedMethodMember(it,
                        beanIntrospection.getBeanType())))
                .collect(Collectors.toList());

        if (beanProperty.isPresent() && !beanMethods.isEmpty()) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanMethods, beanProperty.get()));
        }

        if (beanMethods.size() > 1) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanMethods, null));
        }

        List<InterfaceTypeDefinition> declaringInterfaces =
                InterfaceFieldBinder.getDeclaringInterfaces(typeDefinitionRegistry, objectTypeDefinition,
                        fieldDefinition.getName());

        Class<?> sourceClass = graphQLBeanIntrospectionRegistry.getInterfaceClass(beanIntrospection.getBeanType());

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

        List<BeanDefinitionAndMethod> beanDefinitionAndMethods = graphQLResolversRegistry
                .findTypeExecutableMethods(sourceClass, objectTypeDefinition.getName(), fieldDefinition.getName());

        if (beanDefinitionAndMethods.isEmpty() && !declaringInterfaces.isEmpty()) {
            unboundInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition,
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

    private void processModelMemberField(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                         TypeRuntimeWiring.Builder typeRuntimeWiringBuilder, ModelMember modelMember,
                                         Class<?> sourceClass, List<InterfaceTypeDefinition> declaringInterfaces) {
        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());
        String directive = getResolverDirective(fieldDefinition);
        Precedence precedence = configuration.getPrecedence();

        List<BeanDefinitionAndMethod> resolvers = sourceClass.isPrimitive() || sourceClass.isEnum()
                || sourceClass.isAnnotation()
                ? Collections.emptyList()
                : graphQLResolversRegistry.findTypeExecutableMethods(sourceClass, objectTypeDefinition.getName(),
                        fieldDefinition.getName());

        if (!resolvers.isEmpty()) {
            if (directive == null && precedence == Precedence.STRICT) {
                throw new AmbiguousBindingException(mappingContext, AmbiguityLog.merge(modelMember.members(), toMap(resolvers)));
            }
            if (directive == null) {
                ambiguityLog.record(mappingContext, modelMember, resolvers);
            }
            if (directive == null && precedence == Precedence.PROPERTY_FIRST) {
                if (!declaringInterfaces.isEmpty()) {
                    ownInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
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
            unboundInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                    modelMember.members(), modelMember));
            return;
        }

        if (!declaringInterfaces.isEmpty() && precedence == Precedence.RESOLVER_FIRST) {
            deferredModelMemberFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                    modelMember.members(), modelMember));
            return;
        }

        if (directive != null) {
            throw new ResolverRequiredException(mappingContext, directive, modelMember.members());
        }

        if (!declaringInterfaces.isEmpty()) {
            ownInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                    modelMember.members(), modelMember));
        }
        bindModelMember(objectTypeDefinition, fieldDefinition, typeRuntimeWiringBuilder, modelMember);
    }

    private void bindModelMember(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
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

            fieldBindings.put(coordinate, "property " + beanIntrospection.getBeanType().getName() + "."
                    + getPropertyMethodName(beanProperty));

            argumentPlanner.checkArgumentCount(beanProperty, mappingContext);

            typeRuntimeWiringBuilder.dataFetcher(fieldDefinition.getName(), new MicronautPropertyDataFetcher(beanProperty));

            processFieldReturnType(beanProperty.asArgument(), fieldDefinition.getType(), mappingContext);

            if (manifestRecorder.isRecording()) {
                manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                        () -> BindingManifestRecorder.propertyMember(beanProperty), beanProperty.asArgument(), false);
            }
            return;
        }

        BeanMethod<Object, ?> beanMethod = modelMember.beanMethod();

        fieldBindings.put(coordinate, "method " + beanIntrospection.getBeanType().getName() + "."
                + getExecutableMethodFullName(beanMethod));

        processExecutableMethod(
                beanMethod,
                beanMethod.getReturnType(),
                null,
                null,
                typeRuntimeWiringBuilder,
                mappingContext
        );

        if (manifestRecorder.isRecording()) {
            manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                    modelMember::manifestMember, beanMethod.getReturnType().asArgument(), false);
        }
    }

    private void bindTypeResolver(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                  TypeRuntimeWiring.Builder typeRuntimeWiringBuilder, Class<?> sourceClass,
                                  List<BeanDefinitionAndMethod> beanDefinitionAndMethods,
                                  List<InterfaceTypeDefinition> declaringInterfaces) {
        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        if (beanDefinitionAndMethods.size() > 1) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanDefinitionAndMethods));
        }

        if (!declaringInterfaces.isEmpty()) {
            ownInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition,
                    sourceClass, toMap(beanDefinitionAndMethods), null));
        }

        BeanDefinitionAndMethod beanDefinitionAndMethod = beanDefinitionAndMethods.get(0);

        recordResolverBinding(mappingContext, beanDefinitionAndMethod);

        ExecutableMethod<Object, ?> executable = beanDefinitionAndMethod.getExecutableMethod();

        processExecutableMethod(
                executable,
                executable.getReturnType(),
                sourceClass,
                ResolverInstances.of(applicationContext, beanDefinitionAndMethod.getBeanDefinition()),
                typeRuntimeWiringBuilder,
                mappingContext
        );

        if (manifestRecorder.isRecording()) {
            manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_RESOLVER,
                    () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                    executable.getReturnType().asArgument(), false);
        }
    }

    @Nullable
    private String getResolverDirective(FieldDefinition fieldDefinition) {
        for (String directive : configuration.getResolverDirectives()) {
            if (fieldDefinition.hasDirective(directive)) {
                return directive;
            }
        }
        return null;
    }

    private void validateResolverDirectives() {
        List<String> undeclared = configuration.getResolverDirectives().stream()
                .filter(it -> typeDefinitionRegistry.getDirectiveDefinition(it).isEmpty())
                .sorted()
                .toList();
        if (!undeclared.isEmpty()) {
            throw new ConfigurationException(GraphQLToolsConfiguration.PREFIX + ".resolver-directives lists "
                    + "directives that the schema does not declare: " + String.join(", ", undeclared) + ".");
        }
    }

    private void bindInterfaceFields() {
        InterfaceFieldBinder.InterfaceBindings bindings = new InterfaceFieldBinder(typeDefinitionRegistry,
                graphQLResolversRegistry, Collections.unmodifiableMap(processedTypes), configuration.getPrecedence(),
                manifestRecorder, new InterfaceCallbacks()).bind();
        fieldBindings.putAll(bindings.fieldBindings());
        bindings.resolverBindings().forEach((method, coordinates) ->
                resolverBindings.computeIfAbsent(method, it -> new TreeSet<>()).addAll(coordinates));
        bindingFailures.addAll(bindings.failures());
        ambiguities = ambiguityLog.snapshot().plus(bindings.ambiguities());
    }

    private void collectFailure(MappingContext mappingContext, Runnable action) {
        try {
            action.run();
        } catch (AbstractMappingException e) {
            bindingFailures.add(e);
        } catch (RuntimeException e) {
            bindingFailures.add(new UnexpectedBindingException(mappingContext, e));
        }
    }

    private void throwBindingFailures() {
        if (bindingFailures.isEmpty()) {
            return;
        }
        if (bindingFailures.size() == 1) {
            throw bindingFailures.get(0);
        }
        List<AbstractMappingException> sorted = bindingFailures.stream()
                .sorted(Comparator.comparing(it -> it.getMappingContext().getCoordinate()))
                .toList();
        throw new BindingFailuresException(sorted);
    }

    private MethodNotFoundException methodNotFound(String objectTypeName, Class<?> sourceClass, String fieldName,
                                                   TypeMappingContext mappingContext) {
        MethodNotFoundException exception =
                graphQLResolversRegistry.typeMethodNotFound(sourceClass, fieldName, mappingContext);
        Class<?> contractClass = contractTypes.get(objectTypeName);

        if (contractClass == null) {
            return hintClaimedFields(exception, objectTypeName, fieldName);
        }
        List<Class<?>> declaring = graphQLBeanIntrospectionRegistry.getImplementationClasses(contractClass).stream()
                .filter(it -> BeanIntrospector.SHARED.findIntrospection(it)
                        .map(introspection -> introspection.getProperty(fieldName).isPresent()).orElse(false))
                .toList();

        return hintClaimedFields(exception.forContract(contractClass, declaring), objectTypeName, fieldName);
    }

    private void processIfNotProcessed(TypeDefinition<?> typeDefinition, Class<?> targetClass,
                                       MappingContext mappingContext, Runnable runnable) {
        if (processedTypes.containsKey(typeDefinition.getName())) {
            Class<?> processedClass = processedTypes.get(typeDefinition.getName());

            boolean sameBinding = typeDefinition instanceof ObjectTypeDefinition
                    ? bindsAlike(targetClass, processedClass, mappingContext)
                    : targetClass.equals(processedClass);

            if (!sameBinding) {
                throw new MappingConflictException(
                        mappingContext, getType(typeDefinition), typeDefinition.getName(), targetClass, processedClass
                );
            }

            return;
        }

        processedTypes.put(typeDefinition.getName(), targetClass);

        runnable.run();
    }

    private boolean bindsAlike(Class<?> first, Class<?> second, MappingContext mappingContext) {
        if (first.equals(second)) {
            return true;
        }

        if (!graphQLBeanIntrospectionRegistry.getInterfaceClass(first)
                .equals(graphQLBeanIntrospectionRegistry.getInterfaceClass(second))) {
            return false;
        }

        return graphQLBeanIntrospectionRegistry.getGraphQlTypeBeanIntrospection(mappingContext, first).getBeanType()
                .equals(graphQLBeanIntrospectionRegistry.getGraphQlTypeBeanIntrospection(mappingContext, second)
                        .getBeanType());
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private final class InputCallbacks implements InputTypeBinder.Callbacks {

        @Override
        public void processIfNotProcessed(TypeDefinition<?> typeDefinition, Class<?> targetClass,
                                          MappingContext mappingContext, Runnable runnable) {
            GraphQLRuntimeWiringGenerator.this.processIfNotProcessed(typeDefinition, targetClass, mappingContext,
                    runnable);
        }

        @Override
        public void processEnum(EnumTypeDefinition enumTypeDefinition, Class<?> targetClass, boolean input,
                                MappingContext mappingContext) {
            processEnumTypeDefinition(enumTypeDefinition, targetClass, input, mappingContext);
        }
    }

    private final class InterfaceCallbacks implements InterfaceFieldBinder.Callbacks {

        @Override
        public InterfaceFieldBinder.InterfaceFields takeDiscoveredFields() {
            return new InterfaceFieldBinder.InterfaceFields(drain(unboundInterfaceFields), drain(ownInterfaceFields),
                    drain(deferredModelMemberFields), drain(interfaceResolverFields));
        }

        private static List<InterfaceField> drain(List<InterfaceField> fields) {
            List<InterfaceField> taken = List.copyOf(fields);
            fields.clear();
            return taken;
        }

        @Override
        public void bindModelMember(InterfaceField field) {
            GraphQLRuntimeWiringGenerator.this.bindModelMember(field.objectTypeDefinition(), field.fieldDefinition(),
                    objectTypeWirings.get(field.objectTypeDefinition().getName()), field.modelMember());
        }

        @Override
        public void wireResolver(InterfaceField field, InterfaceResolverMethod method) {
            ExecutableMethod<Object, ?> executable = method.method().getExecutableMethod();

            processExecutableMethod(
                    executable,
                    executable.getReturnType(),
                    method.interfaceClass(),
                    ResolverInstances.of(applicationContext, method.method().getBeanDefinition()),
                    objectTypeWirings.get(field.objectTypeDefinition().getName()),
                    field.mappingContext()
            );

            if (manifestRecorder.isRecording()) {
                manifestRecorder.field(field.objectTypeDefinition(), field.fieldDefinition(),
                        BindingManifestRecorder.KIND_RESOLVER,
                        () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                        executable.getReturnType().asArgument(), false);
            }
        }

        @Override
        public MethodNotFoundException methodNotFound(String objectTypeName, Class<?> sourceClass, String fieldName,
                                                      TypeMappingContext mappingContext) {
            return GraphQLRuntimeWiringGenerator.this.methodNotFound(objectTypeName, sourceClass, fieldName,
                    mappingContext);
        }

        @Override
        public String resolverDirective(FieldDefinition fieldDefinition) {
            return getResolverDirective(fieldDefinition);
        }
    }

}
