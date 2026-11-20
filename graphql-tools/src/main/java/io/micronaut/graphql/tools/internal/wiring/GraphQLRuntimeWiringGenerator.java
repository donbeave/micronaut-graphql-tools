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
import graphql.schema.DataFetcher;
import graphql.schema.idl.MapEnumValuesProvider;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.ReturnType;
import io.micronaut.graphql.tools.internal.exceptions.resolver.ExecuteOnException;
import io.micronaut.graphql.tools.internal.execution.FieldExecutors;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.customizer.RuntimeWiringCustomizer;
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.rules.EnumTypeRules;
import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import io.micronaut.graphql.tools.internal.checks.Ambiguities;
import io.micronaut.graphql.tools.internal.checks.AmbiguityLog;
import io.micronaut.graphql.tools.internal.checks.CheckPolicy;
import io.micronaut.graphql.tools.internal.checks.DictionaryValidator;
import io.micronaut.graphql.tools.internal.checks.SchemaCoordinateValidator;
import io.micronaut.graphql.tools.internal.checks.StrictModeChecks;
import io.micronaut.graphql.tools.internal.checks.UnreachableTypeChecks;
import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.AmbiguousBindingException;
import io.micronaut.graphql.tools.internal.exceptions.BindingFailuresException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.IncorrectClassMappingException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.InterfaceTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.MappingConflictException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.input.MissingEnumValuesException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.ResolverRequiredException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.RootResolverNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.configuration.SchemaDefinitionNotProvidedException;
import io.micronaut.graphql.tools.internal.exceptions.checks.StrictModeException;
import io.micronaut.graphql.tools.internal.exceptions.UnexpectedBindingException;
import io.micronaut.graphql.tools.internal.input.EnumIntrospections;
import io.micronaut.graphql.tools.internal.input.InputTypeBinder;
import io.micronaut.graphql.tools.internal.input.ScalarMappings;
import io.micronaut.graphql.tools.internal.input.TypeDefinitionMappingContext;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.BeanDefinitionAndMethod;
import io.micronaut.graphql.tools.internal.model.GraphQLBeanIntrospectionRegistry;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.internal.model.ModelMember;
import io.micronaut.graphql.tools.internal.model.ModelMemberIndex;
import io.micronaut.graphql.tools.internal.model.RuntimeTypeModel;
import io.micronaut.graphql.tools.internal.resolver.ArgumentDefinition;
import io.micronaut.graphql.tools.internal.resolver.ArgumentPlanner;
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict;
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules;
import io.micronaut.graphql.tools.internal.exceptions.resolver.UnsupportedReturnTypeException;
import io.micronaut.graphql.tools.internal.resolver.MicronautExecutableMethodDataFetcher;
import io.micronaut.graphql.tools.internal.execution.FieldExecutionPlanner;
import io.micronaut.graphql.tools.internal.resolver.MicronautIntrospectionDataFetcher;
import io.micronaut.graphql.tools.internal.resolver.MicronautPropertyDataFetcher;
import io.micronaut.graphql.tools.internal.resolver.ResolverInstances;
import io.micronaut.graphql.tools.internal.resolver.ReturnTypes;
import io.micronaut.inject.ExecutableMethod;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.getSupportedClasses;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.isGraphQlBuiltInType;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.isJavaBuiltInClass;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.getType;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.requireTypeName;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.unsupportedTypeDefinition;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.unwrapNonNullType;
import static io.micronaut.graphql.tools.internal.MicronautUtils.getExecutableMethodFullName;
import static io.micronaut.graphql.tools.internal.MicronautUtils.getPropertyMethodName;
import static io.micronaut.graphql.tools.internal.MicronautUtils.toMap;

/** Generates the graphql-java runtime wiring: binds each schema type and field to a resolver, data fetcher or type resolver. */
@Internal
public final class GraphQLRuntimeWiringGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final ApplicationContext applicationContext;
    private final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final SchemaMappingDictionary schemaMappingDictionary;
    private final GraphQLToolsConfiguration configuration;
    private final CheckPolicy checkPolicy;
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
    private final Map<BeanIntrospection<Object>, ModelMemberIndex> memberIndexes = new IdentityHashMap<>();

    private Ambiguities ambiguities = Ambiguities.NONE;

    private Map<String, String> claimedFields = Map.of();

    private final Set<String> extensionFields;

    private final BindingManifestRecorder manifestRecorder;

    private final SchemaCoordinateValidator coordinateValidator;

    private final DictionaryValidator dictionaryValidator;
    private final DeclaringInterfaces interfaceIndex;

    private final UnreachableTypeChecks unreachableTypeChecks;

    private final RuntimeTypeModel typeModel = new RuntimeTypeModel();

    // The walk from a field to the types it returns is a depth-first walk over the schema, so its depth is the length of
    // the longest chain of types: it runs from this stack of steps instead of recursing once per type.
    private final ArrayDeque<Step> steps = new ArrayDeque<>();

    private boolean draining;

    private Step running;

    private Step lastNested;

    private final InputTypeBinder inputTypeBinder;

    private final ArgumentPlanner argumentPlanner;

    private final FieldExecutors fieldExecutors;

    private final AbstractTypeWiring abstractTypeWiring;

    @Nullable
    private String subscriptionTypeName;

  public   GraphQLRuntimeWiringGenerator(ApplicationContext applicationContext,
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
        this.checkPolicy = CheckPolicy.of(configuration, applicationContext);
        this.extensionFields = extensionFields;
        this.manifestRecorder = new BindingManifestRecorder(configuration.getManifest().isRecorded());
        this.coordinateValidator = new SchemaCoordinateValidator(typeDefinitionRegistry, graphQLResolversRegistry,
                graphQLBeanIntrospectionRegistry, checkPolicy);
        this.dictionaryValidator = new DictionaryValidator(typeDefinitionRegistry, schemaMappingDictionary);
        this.interfaceIndex = new DeclaringInterfaces(typeDefinitionRegistry);
        this.unreachableTypeChecks = new UnreachableTypeChecks(typeDefinitionRegistry, configuration, checkPolicy, manifestRecorder);

        this.scalarMappings = new ScalarMappings(applicationContext.getBeansOfType(GraphQLScalarMapping.class),
                manifestRecorder, applicationContext);

        this.rootRuntimeWiringBuilder = RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.GraphQLShort)
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.GraphQLBigInteger);
        scalarMappings.all().forEach(it -> rootRuntimeWiringBuilder.scalar(it.getScalarType()));
        this.inputTypeBinder = new InputTypeBinder(typeDefinitionRegistry, scalarMappings, configuration,
                checkPolicy, manifestRecorder, typeModel, applicationContext, new InputCallbacks());
        this.argumentPlanner = new ArgumentPlanner(inputTypeBinder, typeModel, applicationContext);
        this.fieldExecutors = new FieldExecutors(applicationContext, configuration);
        this.ambiguityLog = new AmbiguityLog(configuration.getPrecedence(), manifestRecorder);
        this.abstractTypeWiring = new AbstractTypeWiring(typeDefinitionRegistry, schemaMappingDictionary,
                graphQLBeanIntrospectionRegistry, manifestRecorder, rootRuntimeWiringBuilder,
                configuration.getTypeClassConvention());
    }

    public BindingManifestRecorder getManifestRecorder() {
        return manifestRecorder;
    }

    public CheckPolicy getCheckPolicy() {
        return checkPolicy;
    }

    public RuntimeWiring generate(List<RuntimeWiringCustomizer> customizers) {
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

            inputTypeBinder.logNullablePrimitiveWarnings();
            inputTypeBinder.inputClassChecks().checkCollected(checkPolicy, manifestRecorder);

            if (!ambiguities.bindings().isEmpty()) {
                LOG.warn("{} is {}: fields that both a member of the Java type and a type resolver method can bind: {}",
                        GraphQLToolsConfiguration.PREFIX + ".precedence", PrecedenceRules.Setting.valueOf(configuration.getPrecedence().name()).token(),
                        ambiguities.bindings().entrySet().stream()
                                .map(it -> it.getKey() + " (" + it.getValue() + ")")
                                .collect(Collectors.joining("; ")));
            }

            objectTypeWirings.values().forEach(it -> rootRuntimeWiringBuilder.type(it.build()));

            Set<String> implementedInterfaces = abstractTypeWiring.wireInterfaces(snapshot(processedTypes));

            abstractTypeWiring.checkAmbiguity();

            UnreachableTypeChecks.Reachability reachability = unreachableTypeChecks.processUnreachableTypes(
                    schemaDefinition, implementedInterfaces, snapshot(processedTypes), rootRuntimeWiringBuilder);
            Set<String> unboundTypes = reachability.unbound();

            Optional<SchemaBindingException> coordinateFinding = coordinateValidator.verifySchemaCoordinateBindings(
                    unboundTypes, snapshot(processedTypes), snapshot(fieldBindings), immutable(resolverBindings));

            Optional<SchemaBindingException> strictFinding = checkStrictMode(unboundTypes);

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

    private Optional<SchemaBindingException> checkStrictMode(Set<String> unboundTypes) {
        StrictModeChecks checks = new StrictModeChecks(manifestRecorder, checkPolicy);
        boolean recording = manifestRecorder.isRecording();
        Set<String> allowedTypes = new HashSet<>(configuration.getAllow().getUnreachable());
        if (!checkPolicy.failing(BindingCode.UNREACHABLE_TYPE)) {
            allowedTypes.addAll(unboundTypes);
        }

        Map<String, Class<?>> processed = snapshot(processedTypes);
        if (checks.enabled(BindingCode.UNBOUND_RESOLVER_METHOD)) {
            Set<String> accountedMethods = new HashSet<>(resolverBindings.keySet());
            accountedMethods.addAll(ambiguities.shadowedResolverMethods());
            checks.unboundResolverMethods(graphQLResolversRegistry.getResolverMethods(), accountedMethods);
        }
        if (recording) {
            checks.ignoredResolverMethods(graphQLResolversRegistry.getIgnoredMethods());
        }
        if (checks.enabled(BindingCode.ROOT_METHOD_SEVERAL_OPERATIONS)) {
            checks.rootMethodsOfSeveralOperationTypes(rootMethodBindings);
        }
        if (checks.enabled(BindingCode.UNBOUND_ENUM_CONSTANT) || recording) {
            checks.unboundEnumConstants(processed, typeDefinitionRegistry, typeModel);
        }
        if (checks.enabled(BindingCode.UNUSED_DICTIONARY_ENTRY)) {
            checks.unusedDictionaryEntries(schemaMappingDictionary.getTypes(), processed.keySet(), allowedTypes);
        }
        if (checks.enabled(BindingCode.INTERFACE_READ_THROUGH_IMPLEMENTATION)) {
            checks.interfacesReadThroughImplementations(interfacesReadThroughImplementations);
        }
        checks.logWarnings();

        return !checks.getProblems().isEmpty()
                ? Optional.of(new StrictModeException(checks.getCodes(), checks.getProblems()))
                : Optional.empty();
    }

    private static void throwFindings(List<SchemaBindingException> findings) {
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
                                 TypeMappingContext mappingContext, @Nullable AnnotationMetadata owner) {
        mappingContext = TypeMappingContext.forField(
                mappingContext,
                executable.getDeclaringType(),
                getExecutableMethodFullName(executable)
        );

        List<ArgumentDefinition> argumentDefinitions = argumentPlanner.plan(executable, sourceClass,
                mappingContext);

        ReturnVerdict verdict = processFieldReturnType(returnType.asArgument(),
                mappingContext.getFieldDefinition().getType(), mappingContext, true, isSingleResult(executable));

        // decided once per field here: a field that is not async and has no @ExecuteOn keeps the plain method fetcher and allocates nothing more
        Executor fieldExecutor = fieldExecutors.forMethod(executable.getAnnotationMetadata(), owner, mappingContext);
        DataFetcher<Object> fetcher = new MicronautExecutableMethodDataFetcher(executable, argumentDefinitions, instance);
        typeRuntimeWiringBuilder.dataFetcher(
                mappingContext.getFieldDefinition().getName(),
                FieldExecutionPlanner.plan(fetcher, verdict, mappingContext.getGraphQlObjectType().equals(subscriptionTypeName),
                        applicationContext.getConversionService(), fieldExecutor)
        );
    }

    private static boolean isSingleResult(AnnotationMetadataProvider method) {
        return method.getAnnotationMetadata().hasStereotype(AnnotationNames.SINGLE_RESULT);
    }

    private void processFieldReturnType(Argument<?> argument, Type<?> graphQlType, TypeMappingContext mappingContext) {
        // a property is read, never invoked: a Publisher can only be adapted from a method
        if (processFieldReturnType(argument, graphQlType, mappingContext, true, false).reactive() != ReturnVerdict.Reactive.NONE) {
            throw new UnsupportedReturnTypeException(mappingContext, WrapperRules.publisherOnProperty());
        }
    }

    private ReturnVerdict processFieldReturnType(Argument<?> argument, Type<?> graphQlType,
                                                 TypeMappingContext mappingContext, boolean fieldLevel,
                                                 boolean singleResult) {
        boolean subscription = mappingContext.getGraphQlObjectType().equals(subscriptionTypeName);
        ReturnVerdict verdict = ReturnTypes.adaptation(typeModel, typeDefinitionRegistry, argument, graphQlType, fieldLevel, subscription,
                singleResult, mappingContext);
        if (verdict.reactive() == ReturnVerdict.Reactive.VALUELESS) {
            // the field is null: no value type to check against the schema
            return verdict;
        }
        argument = ReturnTypes.unwrap(argument, verdict);
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

            processFieldReturnType(listArgument, listFieldType, mappingContext, false, false);
            return verdict;
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
                nested(() -> processUnionTypeDefinition(unionTypeDefinition, returnClass, mappingContext));
            } else if (typeDefinition instanceof ObjectTypeDefinition objectTypeDefinition) {
                nested(() -> processObjectTypeDefinition(objectTypeDefinition, returnClass, mappingContext));
            } else if (typeDefinition instanceof InterfaceTypeDefinition interfaceTypeDefinition) {
                nested(() -> processInterfaceTypeDefinition(interfaceTypeDefinition, returnClass, mappingContext));
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
        return verdict;
    }

    private void processOperationTypeDefinition(OperationTypeDefinition operationTypeDefinition) {
        ObjectTypeDefinition objectTypeDefinition = typeDefinitionRegistry
                .getType(operationTypeDefinition.getTypeName(), ObjectTypeDefinition.class).get();

        manifestRecorder.type(objectTypeDefinition.getName(), "object", null);

        rootRuntimeWiringBuilder.type(operationTypeDefinition.getTypeName().getName(), typeRuntimeWiringBuilder -> {
            for (FieldDefinition fieldDefinition : objectTypeDefinition.getFieldDefinitions()) {
                collectFailure(TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName()),
                        () -> processRootFieldDefinition(operationTypeDefinition.getName(), objectTypeDefinition,
                        fieldDefinition, typeRuntimeWiringBuilder));
            }

            return typeRuntimeWiringBuilder;
        });
    }

    private void processRootFieldDefinition(String operation, ObjectTypeDefinition objectTypeDefinition,
                                            FieldDefinition fieldDefinition, TypeRuntimeWiring.Builder typeRuntimeWiringBuilder) {
        if (isClaimed(objectTypeDefinition, fieldDefinition)) {
            return;
        }

        TypeMappingContext mappingContext =
                TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        List<BeanDefinitionAndMethod> beanDefinitionAndMethods;
        try {
            beanDefinitionAndMethods = graphQLResolversRegistry.getRootExecutableMethods(
                    operation, objectTypeDefinition.getName(), fieldDefinition.getName(), mappingContext);
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
                mappingContext,
                null
        );

        rootMethodBindings.computeIfAbsent(executable, it -> new TreeSet<>())
                .add(objectTypeDefinition.getName() + "." + fieldDefinition.getName());

        if (manifestRecorder.isRecording()) {
            manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_ROOT,
                    () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                    executable.getReturnType().asArgument(),
                    objectTypeDefinition.getName().equals(subscriptionTypeName), isSingleResult(executable));
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
                    () -> new ClassNotIntrospectedException(new TypeDefinitionMappingContext(enumTypeDefinition, targetClass, mappingContext),
                            targetClass, Introspected.class));
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
                    (objectTypeDefinition, memberClass, memberContext) ->
                            nested(() -> processObjectTypeDefinition(objectTypeDefinition, memberClass, memberContext)),
                    this::inOrder);
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

            List<Runnable> members = new ArrayList<>();
            for (ObjectTypeDefinition implementation
                    : abstractTypeWiring.getImplementations(interfaceTypeDefinition)) {
                members.add(() -> {
                    Class<?> clazz = Optional
                            .ofNullable(abstractTypeWiring.findClass(implementation, mappingContext))
                            .orElseThrow(() -> new InterfaceTypeMappingNotProvidedException(
                                    mappingContext, implementation.getName(), interfaceTypeDefinition.getName()
                            ));

                    AbstractTypeWiring.checkMemberClass("interface", interfaceTypeDefinition.getName(), targetClass,
                            implementation.getName(), clazz, mappingContext);

                    nested(() -> processObjectTypeDefinition(implementation, clazz, mappingContext));
                });
            }
            inOrder(members);
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

            List<FieldDefinition> fieldDefinitions = objectTypeDefinition.getFieldDefinitions();
            for (int i = fieldDefinitions.size() - 1; i >= 0; i--) {
                FieldDefinition fieldDefinition = fieldDefinitions.get(i);
                steps.push(new Step(() -> processFieldDefinition(
                        fieldDefinition, objectTypeDefinition, typeRuntimeWiringBuilder, beanIntrospection),
                        TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName())));
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

        ModelMemberIndex members = memberIndexes.computeIfAbsent(beanIntrospection, ModelMemberIndex::new);
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
                interfaceIndex.of(objectTypeDefinition, fieldDefinition.getName());

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
        PrecedenceRules.Setting setting = PrecedenceRules.Setting.valueOf(configuration.getPrecedence().name());

        List<BeanDefinitionAndMethod> resolvers = sourceClass.isPrimitive() || sourceClass.isEnum()
                || sourceClass.isAnnotation()
                ? Collections.emptyList()
                : graphQLResolversRegistry.findTypeExecutableMethods(sourceClass, objectTypeDefinition.getName(),
                        fieldDefinition.getName());

        if (!resolvers.isEmpty()) {
            boolean intent = directive != null || resolvers.stream().anyMatch(graphQLResolversRegistry::declaresCoordinate);
            PrecedenceRules.Outcome outcome = PrecedenceRules.decide(setting, intent);
            if (outcome == PrecedenceRules.Outcome.FAIL) {
                throw new AmbiguousBindingException(mappingContext, AmbiguityLog.merge(modelMember.members(), toMap(resolvers)));
            }
            if (!intent) {
                ambiguityLog.record(mappingContext, modelMember, resolvers);
            }
            if (outcome == PrecedenceRules.Outcome.MEMBER) {
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

        if (!declaringInterfaces.isEmpty() && (setting == PrecedenceRules.Setting.RESOLVER_FIRST
                || declaresInterfaceCoordinate(declaringInterfaces, fieldDefinition.getName()))) {
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

    private boolean declaresInterfaceCoordinate(List<InterfaceTypeDefinition> interfaces, String fieldName) {
        return interfaces.stream().anyMatch(it -> graphQLResolversRegistry.isCoordinateDeclared(
                it.getName() + "." + fieldName));
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

            if (beanProperty.getAnnotationMetadata().hasDeclaredAnnotation(ExecuteOn.class)) {
                throw new ExecuteOnException(BindingCode.EXECUTE_ON_PROPERTY, "@ExecuteOn is on an accessor that binds this field "
                        + "as a property, and a property is read, never run on another executor. Fix: make it a @GraphQLField "
                        + "method, or move the field to a type resolver, and put @ExecuteOn there.", mappingContext);
            }

            typeRuntimeWiringBuilder.dataFetcher(fieldDefinition.getName(), new MicronautPropertyDataFetcher(beanProperty));

            processFieldReturnType(beanProperty.asArgument(), fieldDefinition.getType(), mappingContext);

            if (manifestRecorder.isRecording()) {
                manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                        () -> BindingManifestRecorder.propertyMember(beanProperty), beanProperty.asArgument(), false, false);
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
                mappingContext,
                beanIntrospection.getAnnotationMetadata()
        );

        if (manifestRecorder.isRecording()) {
            manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                    modelMember::manifestMember, beanMethod.getReturnType().asArgument(), false,
                    isSingleResult(beanMethod));
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
                mappingContext,
                null
        );

        if (manifestRecorder.isRecording()) {
            manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_RESOLVER,
                    () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                    executable.getReturnType().asArgument(), false, isSingleResult(executable));
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
        InterfaceFieldBinder.InterfaceBindings bindings = new InterfaceFieldBinder(interfaceIndex,
                graphQLResolversRegistry, Collections.unmodifiableMap(processedTypes), configuration.getPrecedence(),
                manifestRecorder, new InterfaceCallbacks()).bind();
        fieldBindings.putAll(bindings.fieldBindings());
        bindings.resolverBindings().forEach((method, coordinates) ->
                resolverBindings.computeIfAbsent(method, it -> new TreeSet<>()).addAll(coordinates));
        bindingFailures.addAll(bindings.failures());
        ambiguities = ambiguityLog.snapshot().plus(bindings.ambiguities());
    }

    private boolean collectFailure(MappingContext mappingContext, Runnable action) {
        try {
            action.run();
            return true;
        } catch (AbstractMappingException e) {
            bindingFailures.add(e);
        } catch (RuntimeException e) {
            bindingFailures.add(new UnexpectedBindingException(mappingContext, e));
        }
        return false;
    }

    /**
     * Runs the work as the recursive call it stands for: inside a running walk it becomes the next step, which runs
     * before the steps that were already waiting, so the order is the one of a recursive walk. A failure of the step
     * is reported on the field that reached it, as the recursive call's failure would have been.
     */
    private void nested(Runnable work) {
        if (!draining) {
            drain(new Step(work, null));
            return;
        }
        Step step = new Step(work, running.context);
        steps.push(step);
        lastNested = step;
    }

    private void drain(Step first) {
        draining = true;
        steps.push(first);
        try {
            while (!steps.isEmpty()) {
                Step step = steps.pop();
                if (step.cancelled) {
                    continue;
                }
                Step previous = running;
                running = step;
                try {
                    step.run();
                } finally {
                    running = previous;
                }
            }
        } finally {
            steps.clear();
            draining = false;
        }
    }

    /**
     * Runs the actions one after the other, each with the steps it starts, and stops at the first one that fails, as
     * a loop that calls them does.
     */
    private void inOrder(List<Runnable> actions) {
        inOrder(actions, 0);
    }

    private void inOrder(List<Runnable> actions, int index) {
        if (index == actions.size()) {
            return;
        }
        Step rest = new Step(() -> inOrder(actions, index + 1), running.context);
        steps.push(rest);
        lastNested = null;
        try {
            actions.get(index).run();
        } catch (RuntimeException e) {
            rest.cancelled = true;
            throw e;
        }
        if (lastNested != null) {
            lastNested.after = rest;
        }
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
                    field.mappingContext(),
                    null
            );

            if (manifestRecorder.isRecording()) {
                manifestRecorder.field(field.objectTypeDefinition(), field.fieldDefinition(),
                        BindingManifestRecorder.KIND_RESOLVER,
                        () -> BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                        executable.getReturnType().asArgument(), false, isSingleResult(executable));
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

    private final class Step {

        private final Runnable action;
        private final MappingContext context;
        private boolean cancelled;
        private Step after;

        private Step(Runnable action, MappingContext context) {
            this.action = action;
            this.context = context;
        }

        private void run() {
            if (context == null) {
                action.run();
            } else if (!collectFailure(context, action) && after != null) {
                after.cancelled = true;
            }
        }
    }
}
