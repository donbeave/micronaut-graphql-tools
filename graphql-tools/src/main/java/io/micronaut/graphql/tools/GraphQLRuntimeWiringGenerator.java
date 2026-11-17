/*
 * Copyright 2021-2022 original authors
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
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.ReturnType;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration.Precedence;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException;
import io.micronaut.graphql.tools.exceptions.BindingFailuresException;
import io.micronaut.graphql.tools.exceptions.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException;
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException;
import io.micronaut.graphql.tools.exceptions.IncorrectMemberMappingException;
import io.micronaut.graphql.tools.exceptions.InterfaceTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.exceptions.InputFieldNotSettableException;
import io.micronaut.graphql.tools.exceptions.InvalidScalarMappingException;
import io.micronaut.graphql.tools.exceptions.InvalidSourceArgumentException;
import io.micronaut.graphql.tools.exceptions.MappingConflictException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.exceptions.MissingEnumValuesException;
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.exceptions.ResolverRequiredException;
import io.micronaut.graphql.tools.exceptions.RootResolverNotFoundException;
import io.micronaut.graphql.tools.exceptions.ScalarMappingNotProvidedException;
import io.micronaut.graphql.tools.exceptions.SchemaDefinitionNotProvidedException;
import io.micronaut.graphql.tools.exceptions.StrictModeException;
import io.micronaut.graphql.tools.exceptions.UnionTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.schema.AbstractTypeResolver;
import io.micronaut.graphql.tools.exceptions.UnsupportedInputTypeException;
import io.micronaut.graphql.tools.schema.ArgumentValueModule;
import io.micronaut.graphql.tools.schema.DefaultWiringFactory;
import io.micronaut.graphql.tools.schema.InputValueConverter;
import io.micronaut.graphql.tools.schema.MicronautExecutableMethodDataFetcher;
import io.micronaut.graphql.tools.schema.MicronautIntrospectionDataFetcher;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jackson.modules.BeanIntrospectionModule;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import static io.micronaut.graphql.tools.GraphQLUtils.printType;
import static io.micronaut.graphql.tools.GraphQLUtils.unwrapNonNullType;
import static io.micronaut.graphql.tools.MicronautUtils.getExecutableMethodFullName;
import static io.micronaut.graphql.tools.MicronautUtils.getMethodName;
import static io.micronaut.graphql.tools.MicronautUtils.getPropertyMethodName;
import static io.micronaut.graphql.tools.MicronautUtils.toMap;
import static io.micronaut.graphql.tools.MicronautUtils.unwrapArgument;
import static io.micronaut.graphql.tools.SystemTypes.getSupportedClasses;
import static io.micronaut.graphql.tools.SystemTypes.isGraphQlBuiltInType;
import static io.micronaut.graphql.tools.SystemTypes.isJavaBuiltInClass;

/**
 * @author Alexey Zhokhov
 */
@Internal
final class GraphQLRuntimeWiringGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final ApplicationContext applicationContext;
    private final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final SchemaMappingDictionary schemaMappingDictionary;
    private final Provider<GraphQLSchema> graphQLSchemaProvider;
    private final InputValueConverter inputValueConverter;
    private final Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans = new ConcurrentHashMap<>();
    private final InputClassChecks inputClassChecks;
    private final GraphQLToolsConfiguration configuration;
    private final Map<String, GraphQLScalarMapping> scalarMappings;
    private final RuntimeWiring.Builder rootRuntimeWiringBuilder;

    private final Map<String, Class<?>> processedTypes = new HashMap<>();
    // object type -> the Java interface whose own introspection its properties are read through
    private final Map<String, Class<?>> contractTypes = new HashMap<>();

    // key: schema coordinate (Type.field), value: description of the member bound to it
    private final Map<String, String> fieldBindings = new HashMap<>();

    // key: resolver bean class and method, value: the schema coordinates the method is bound to
    private final Map<String, Set<String>> resolverBindings = new HashMap<>();

    // key: object type name, value: its wiring, registered once all its fields are bound (interface fields are bound
    // last, and graphql-java does not merge two wirings of one type the same way in every version)
    private final Map<String, TypeRuntimeWiring.Builder> objectTypeWirings = new LinkedHashMap<>();

    // interface fields that the class of an implementing object type does not bind: bound by an interface resolver
    // once the Java types of all reached interfaces are known
    private final List<InterfaceField> unboundInterfaceFields = new ArrayList<>();

    // interface fields bound by an interface resolver
    private final List<InterfaceField> interfaceResolverFields = new ArrayList<>();

    // interface fields that the class of an implementing object type binds itself (property, method, type resolver)
    private final List<InterfaceField> ownInterfaceFields = new ArrayList<>();

    // interface fields that a member of the class of an implementing object type can bind, decided once the Java types
    // of all reached interfaces are known (precedence resolver-first: an interface resolver wins)
    private final List<InterfaceField> deferredModelMemberFields = new ArrayList<>();

    // key: root resolver method, value: the root fields it is bound to
    private final Map<String, Set<String>> rootMethodBindings = new TreeMap<>();

    // resolver methods that precedence property-first left unbound in favour of a member of the Java type
    private final Set<String> shadowedResolverMethods = new HashSet<>();

    // key: object type mapped to a Java interface without a bean introspection of its own, value: the interface and
    // the implementation class whose introspection is read instead
    private final Map<String, List<Class<?>>> interfacesReadThroughImplementations = new TreeMap<>();

    // failures of fields that cannot be bound, reported together once every field was tried
    private final List<AbstractMappingException> bindingFailures = new ArrayList<>();

    // key: schema coordinate (Type.field) that a member and a type resolver can both bind, decided by the precedence,
    // value: the members
    private final Map<String, String> ambiguousBindings = new TreeMap<>();

    private final BindingManifestRecorder manifestRecorder = new BindingManifestRecorder();

    private final SchemaChecks schemaChecks;

    @Nullable
    private String subscriptionTypeName;

    GraphQLRuntimeWiringGenerator(ApplicationContext applicationContext,
                                  GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry,
                                  GraphQLResolversRegistry graphQLResolversRegistry,
                                  TypeDefinitionRegistry typeDefinitionRegistry,
                                  SchemaMappingDictionary schemaMappingDictionary,
                                  Provider<GraphQLSchema> graphQLSchemaProvider,
                                  GraphQLToolsConfiguration configuration) {
        requireNonNull("applicationContext", applicationContext);
        requireNonNull("graphQLBeanIntrospectionRegistry", graphQLBeanIntrospectionRegistry);
        requireNonNull("graphQLResolversRegistry", graphQLResolversRegistry);
        requireNonNull("typeDefinitionRegistry", typeDefinitionRegistry);
        requireNonNull("schemaParserDictionary", schemaMappingDictionary);
        requireNonNull("graphQLSchemaProvider", graphQLSchemaProvider);
        requireNonNull("configuration", configuration);

        this.applicationContext = applicationContext;
        this.graphQLBeanIntrospectionRegistry = graphQLBeanIntrospectionRegistry;
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.schemaMappingDictionary = schemaMappingDictionary;
        this.graphQLSchemaProvider = graphQLSchemaProvider;
        this.configuration = configuration;
        this.schemaChecks = new SchemaChecks(typeDefinitionRegistry, graphQLResolversRegistry,
                graphQLBeanIntrospectionRegistry, schemaMappingDictionary, configuration, manifestRecorder);

        this.scalarMappings = collectScalarMappings(applicationContext.getBeansOfType(GraphQLScalarMapping.class));

        this.inputValueConverter = new InputValueConverter(
                createInputObjectMapper(applicationContext, scalarMappings.values(), configuration),
                omissionPlans
        );
        this.inputClassChecks = new InputClassChecks(inputValueConverter, omissionPlans);
        this.rootRuntimeWiringBuilder = RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.GraphQLShort)
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.GraphQLBigInteger);
        scalarMappings.values().forEach(it -> rootRuntimeWiringBuilder.scalar(it.getScalarType()));
    }

    /**
     * @return a copy of the application's {@link GraphQLToolsConfiguration#INPUT_OBJECT_MAPPER} bean if there is one,
     * otherwise a mapper with Jackson's defaults and the {@link BeanIntrospectionModule}; in both cases with the
     * library's own modules added ({@link ArgumentValueModule} only with {@code input.detect-omission})
     */
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

    private static Map<String, GraphQLScalarMapping> collectScalarMappings(
            Collection<GraphQLScalarMapping> scalarMappings) {
        Map<String, GraphQLScalarMapping> result = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        Set<String> builtIn = new TreeSet<>();
        for (GraphQLScalarMapping scalarMapping : scalarMappings) {
            String name = scalarMapping.getName();
            counts.merge(name, 1, Integer::sum);
            if (isGraphQlBuiltInType(name)) {
                builtIn.add(name);
            }
            result.put(name, scalarMapping);
        }
        Map<String, Integer> duplicates = new TreeMap<>(counts);
        duplicates.values().removeIf(count -> count == 1);
        if (!duplicates.isEmpty() || !builtIn.isEmpty()) {
            throw new InvalidScalarMappingException(duplicates, List.copyOf(builtIn));
        }
        return result;
    }

    /**
     * @return the lines of the binding manifest recorded by {@link #generate()}
     */
    BindingManifestRecorder getManifestRecorder() {
        return manifestRecorder;
    }

    /**
     * Wires every field the library binds, then applies the customizers.
     *
     * @param customizers the {@link RuntimeWiringCustomizer} beans, in {@code Ordered} order
     * @return the runtime wiring
     */
    RuntimeWiring generate(List<RuntimeWiringCustomizer> customizers) {
        long start = System.nanoTime();

        try {
            SchemaDefinition schemaDefinition = typeDefinitionRegistry.schemaDefinition()
                    .orElseThrow(SchemaDefinitionNotProvidedException::new);

            if (!graphQLResolversRegistry.hasRootResolvers()) {
                throw new RootResolverNotFoundException();
            }

            graphQLResolversRegistry.verifyTypeResolverContracts();

            schemaChecks.validateSchemaCoordinates(schemaDefinition);

            schemaChecks.validateSchemaMappingDictionary();

            validateResolverDirectives();

            subscriptionTypeName = schemaDefinition.getOperationTypeDefinitions().stream()
                    .filter(it -> "subscription".equals(it.getName()))
                    .map(it -> it.getTypeName().getName())
                    .findFirst()
                    .orElse(null);

            for (OperationTypeDefinition operationTypeDefinition : schemaDefinition.getOperationTypeDefinitions()) {
                processOperationTypeDefinition(operationTypeDefinition);
            }

            bindInterfaceFields();

            // the checks below would only repeat what partial bindings left out
            throwBindingFailures();

            GraphQLToolsConfiguration.UnmappedProperties unmappedProperties =
                    configuration.getInput().getUnmappedProperties();
            inputClassChecks.checkCollected(unmappedProperties == null
                    ? configuration.isStrict()
                    : unmappedProperties == GraphQLToolsConfiguration.UnmappedProperties.FAIL, manifestRecorder);

            if (!ambiguousBindings.isEmpty()) {
                LOG.warn("{} is {}: fields that both a member of the Java type and a type resolver method can bind: {}",
                        GraphQLToolsConfiguration.PREFIX + ".precedence", configuration.getPrecedence(),
                        ambiguousBindings.entrySet().stream()
                                .map(it -> it.getKey() + " (" + it.getValue() + ")")
                                .collect(Collectors.joining("; ")));
            }

            objectTypeWirings.values().forEach(it -> rootRuntimeWiringBuilder.type(it.build()));

            Set<String> implementedInterfaces = wireInterfaceTypeResolvers();

            Set<String> unboundTypes = schemaChecks.processUnreachableTypes(schemaDefinition, implementedInterfaces,
                    processedTypes, rootRuntimeWiringBuilder);

            schemaChecks.verifySchemaCoordinateBindings(unboundTypes, processedTypes, fieldBindings, resolverBindings);

            checkStrictMode(unboundTypes);

            rootRuntimeWiringBuilder.wiringFactory(new DefaultWiringFactory(unboundTypes));

            RuntimeWiringCustomizers.apply(rootRuntimeWiringBuilder, customizers, typeDefinitionRegistry);

            return rootRuntimeWiringBuilder.build();
        } finally {
            if (LOG.isInfoEnabled()) {
                long took = elapsedMillis(start);
                LOG.info("GraphQL runtime wiring generated in {}ms.", took);
            }
        }
    }

    /**
     * Records the strict-mode findings in the binding manifest, and fails with all of them in strict mode.
     *
     * @param unboundTypes the unreachable types left unbound on purpose
     */
    private void checkStrictMode(Set<String> unboundTypes) {
        Set<String> allowedTypes = new HashSet<>(configuration.getAllowUnreachable());
        if (configuration.getUnreachableTypes() == GraphQLToolsConfiguration.UnreachableTypes.IGNORE) {
            allowedTypes.addAll(unboundTypes);
        }

        StrictModeChecks checks = new StrictModeChecks(manifestRecorder);
        Set<String> accountedMethods = new HashSet<>(resolverBindings.keySet());
        accountedMethods.addAll(shadowedResolverMethods);
        checks.unboundResolverMethods(graphQLResolversRegistry.getResolverMethods(), accountedMethods);
        checks.rootMethodsOfSeveralOperationTypes(rootMethodBindings);
        checks.unboundEnumConstants(processedTypes, typeDefinitionRegistry);
        checks.unusedDictionaryEntries(schemaMappingDictionary.getTypes(), processedTypes.keySet(), allowedTypes);
        checks.interfacesReadThroughImplementations(interfacesReadThroughImplementations);

        if (configuration.isStrict() && !checks.getProblems().isEmpty()) {
            throw new StrictModeException(checks.getProblems());
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

        List<ArgumentDefinition> argumentDefinitions = calculateArgumentDefinitions(executable, sourceClass,
                mappingContext);

        typeRuntimeWiringBuilder.dataFetcher(
                mappingContext.getFieldDefinition().getName(),
                new MicronautExecutableMethodDataFetcher(inputValueConverter, executable, argumentDefinitions, instance)
        );

        processFieldReturnType(returnType.asArgument(), mappingContext.getFieldDefinition().getType(),
                mappingContext);
    }

    private void checkArgumentCount(Executable<?, ?> executable, @Nullable Class<?> sourceClass,
                                    TypeMappingContext mappingContext) {
        int requiredArgs = mappingContext.getFieldDefinition().getInputValueDefinitions().size();

        if (sourceClass != null) {
            requiredArgs = requiredArgs + 1;
        }

        if (requiredArgs == 0 && executable.getArguments().length == 0) {
            return;
        }

        int currentArgs = (int) Arrays.stream(executable.getArguments())
                .filter(it -> !it.getType().equals(DataFetchingEnvironment.class))
                .count();

        if (requiredArgs == currentArgs) {
            return;
        }

        if (currentArgs > requiredArgs) {
            throw new IncorrectArgumentCountException(
                    mappingContext,
                    false,
                    currentArgs,
                    requiredArgs,
                    generateSuggestedMethod(getMethodName(executable), sourceClass, mappingContext)
            );
        } else {
            throw new IncorrectArgumentCountException(
                    mappingContext,
                    true,
                    currentArgs,
                    requiredArgs,
                    generateSuggestedMethod(getMethodName(executable), sourceClass, mappingContext)
            );
        }
    }

    private void checkArgumentCount(BeanProperty<?, ?> beanProperty, TypeMappingContext mappingContext) {
        if (mappingContext.getFieldDefinition().getInputValueDefinitions().isEmpty()) {
            return;
        }

        int requiredArgs = mappingContext.getFieldDefinition().getInputValueDefinitions().size();

        throw new IncorrectArgumentCountException(
                mappingContext,
                true,
                0,
                requiredArgs,
                generateSuggestedMethod(beanProperty.getName(), null, mappingContext)
        );
    }

    private String generateSuggestedMethod(String methodName, @Nullable Class<?> sourceClass,
                                           TypeMappingContext mappingContext) {
        ArrayList<String> suggestedMethodArgs = new ArrayList<>();

        if (sourceClass != null) {
            String parameterName = mappingContext.getObjectTypeDefinition().getName().substring(0, 1).toLowerCase() +
                    mappingContext.getObjectTypeDefinition().getName().substring(1);
            suggestedMethodArgs.add(sourceClass.getName() + " " + parameterName);
        }

        suggestedMethodArgs.addAll(
                mappingContext.getFieldDefinition().getInputValueDefinitions().stream()
                        .map(it -> getTypeName(it.getType()).getName() + " " + it.getName())
                        .toList()
        );

        if (suggestedMethodArgs.isEmpty()) {
            return null;
        }

        return methodName + "(" + String.join(", ", suggestedMethodArgs) + ")";
    }

    private List<ArgumentDefinition> calculateArgumentDefinitions(Executable<?, ?> executable,
                                                                  @Nullable Class<?> sourceClass,
                                                                  TypeMappingContext mappingContext) {
        checkArgumentCount(executable, sourceClass, mappingContext);

        List<InputValueDefinition> inputs = mappingContext.getFieldDefinition().getInputValueDefinitions();
        List<Argument<?>> arguments = Arrays.stream(executable.getArguments()).collect(Collectors.toList());

        if (inputs.isEmpty() && arguments.isEmpty()) {
            return Collections.emptyList();
        }

        boolean containsSourceArgument = false;

        if (sourceClass != null) {
            if (arguments.get(0).getType().equals(sourceClass)) {
                arguments.remove(0);

                containsSourceArgument = true;
            } else {
                throw new InvalidSourceArgumentException(
                        mappingContext, arguments.get(0).getType(), sourceClass
                );
            }
        }

        boolean containsEnvironmentArgument = false;

        if (!arguments.isEmpty()
                && arguments.get(arguments.size() - 1).getType().equals(DataFetchingEnvironment.class)) {
            arguments.remove(arguments.size() - 1);

            containsEnvironmentArgument = true;
        }

        ArrayList<ArgumentDefinition> result = new ArrayList<>();

        if (containsSourceArgument) {
            result.add(ArgumentDefinition.ofSourceArgument());
        }

        for (int i = 0; i < inputs.size(); i++) {
            InputValueDefinition inputValueDefinition = inputs.get(i);
            Argument<?> argument = arguments.get(i);

            processInputType(
                    inputValueDefinition.getType(),
                    argument,
                    TypeMappingContext.forArgument(mappingContext, inputValueDefinition.getName())
            );

            result.add(ArgumentDefinition.ofInputValueArgument(inputs.get(i).getName(), argument));
        }

        if (containsEnvironmentArgument) {
            result.add(ArgumentDefinition.ofDataFetchingEnvironmentArgument());
        }

        return result;
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
                GraphQLScalarMapping scalarMapping = requireScalarMapping(scalarTypeDefinition, mappingContext);

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
                collectFailure(() -> processRootFieldDefinition(objectTypeDefinition, fieldDefinition,
                        typeRuntimeWiringBuilder));
            }

            return typeRuntimeWiringBuilder;
        });
    }

    private void processRootFieldDefinition(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                            TypeRuntimeWiring.Builder typeRuntimeWiringBuilder) {
        TypeMappingContext mappingContext =
                TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        List<BeanDefinitionAndMethod> beanDefinitionAndMethods =
                graphQLResolversRegistry.getRootExecutableMethods(objectTypeDefinition.getName(),
                        fieldDefinition.getName(), mappingContext);

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
                applicationContext.getBean(beanDefinitionAndMethod.getBeanDefinition()),
                typeRuntimeWiringBuilder,
                mappingContext
        );

        rootMethodBindings.computeIfAbsent(BindingManifestRecorder.methodMember(executable,
                executable.getDeclaringType()), it -> new TreeSet<>())
                .add(objectTypeDefinition.getName() + "." + fieldDefinition.getName());

        manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_ROOT,
                BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                executable.getReturnType().asArgument(),
                BindingManifestRecorder.genericReturnType(executable),
                objectTypeDefinition.getName().equals(subscriptionTypeName));
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

            List<String> existingValues = Arrays.stream(targetClass.getEnumConstants())
                    .map(it -> ((Enum<?>) it).name())
                    .distinct()
                    .sorted()
                    .toList();

            List<String> missingValues = expectedValues.stream()
                    .filter(it -> !existingValues.contains(it))
                    .toList();

            if (!missingValues.isEmpty()) {
                throw new MissingEnumValuesException(mappingContext, targetClass, missingValues);
            }

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

            rootRuntimeWiringBuilder.type(unionTypeDefinition.getName(), typeRuntimeWiringBuilder -> {
                Map<Class<?>, String> exactClasses = new HashMap<>();
                Map<String, Class<?>> objectTypes = new HashMap<>();

                for (Type<?> type : unionTypeDefinition.getMemberTypes()) {
                    TypeName typeName = getTypeName(type);
                    TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(typeName).get();

                    if (typeDefinition instanceof ObjectTypeDefinition objectTypeDefinition) {
                        Class<?> clazz = Optional
                                .ofNullable(schemaMappingDictionary.getTypes().get(typeDefinition.getName()))
                                .orElseThrow(() -> new UnionTypeMappingNotProvidedException(
                                        mappingContext, typeName.getName(), unionTypeDefinition.getName()
                                ));

                        checkMemberClass("union", unionTypeDefinition.getName(), targetClass, typeName.getName(), clazz,
                                mappingContext);

                        processObjectTypeDefinition(objectTypeDefinition, clazz, mappingContext);

                        objectTypes.put(typeName.getName(), clazz);
                        manifestRecorder.resolves(unionTypeDefinition.getName(), typeName.getName(), clazz);
                        for (Class<?> mappedClass : getMappedClasses(clazz)) {
                            exactClasses.put(mappedClass, typeName.getName());
                        }
                    } else {
                        throw unsupportedTypeDefinition(typeDefinition);
                    }
                }

                typeRuntimeWiringBuilder.typeResolver(new AbstractTypeResolver(
                        graphQLSchemaProvider, "union", unionTypeDefinition.getName(), exactClasses, objectTypes
                ));
                return typeRuntimeWiringBuilder;
            });
        });
    }

    /**
     * Maps a GraphQL interface returned by a field to the declared Java interface, and binds every object type that
     * implements it, each to the class registered for it in the {@link SchemaMappingDictionary}. The type resolver is
     * registered by {@link #wireInterfaceTypeResolvers()}.
     *
     * @param interfaceTypeDefinition the interface
     * @param targetClass             the declared Java type
     * @param mappingContext          the field that returns the interface
     */
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

            for (ObjectTypeDefinition implementation : getImplementations(interfaceTypeDefinition)) {
                Class<?> clazz = Optional
                        .ofNullable(schemaMappingDictionary.getTypes().get(implementation.getName()))
                        .orElseThrow(() -> new InterfaceTypeMappingNotProvidedException(
                                mappingContext, implementation.getName(), interfaceTypeDefinition.getName()
                        ));

                checkMemberClass("interface", interfaceTypeDefinition.getName(), targetClass, implementation.getName(),
                        clazz, mappingContext);

                processObjectTypeDefinition(implementation, clazz, mappingContext);
            }
        });
    }

    /**
     * The Java type a bound object type is resolved by: the class the {@link SchemaMappingDictionary} maps it to, which
     * every implementation of a returned interface has, so that the result does not depend on which of two alike
     * classes (see {@link #bindsAlike}) a field reached the type through first. Without a dictionary entry, the class
     * the type was bound with.
     *
     * @param objectTypeDefinition the object type
     * @return the Java type, or {@code null} if the object type is not bound
     */
    @Nullable
    private Class<?> getBoundClass(ObjectTypeDefinition objectTypeDefinition) {
        Class<?> processedClass = processedTypes.get(objectTypeDefinition.getName());
        if (processedClass == null) {
            return null;
        }
        return schemaMappingDictionary.getTypes().getOrDefault(objectTypeDefinition.getName(), processedClass);
    }

    /**
     * An object a field returns is an instance of the Java type the field declares for the union or interface, so an
     * object type of it must be mapped to a subtype of that Java type, or no object can ever resolve to it.
     */
    private static void checkMemberClass(String abstractTypeKind, String abstractTypeName, Class<?> declaredClass,
                                         String objectTypeName, Class<?> memberClass,
                                         TypeMappingContext mappingContext) {
        if (!declaredClass.isAssignableFrom(memberClass)) {
            throw new IncorrectMemberMappingException(mappingContext, abstractTypeKind, abstractTypeName,
                    declaredClass, objectTypeName, memberClass);
        }
    }

    /**
     * @param clazz the Java type an object type is mapped to
     * @return the classes whose objects resolve to that object type by their exact class: the type itself and, for a
     * Java interface, the classes annotated with {@code @GraphQLType(theInterface)}, as an object is never an instance
     * of the interface itself
     */
    private List<Class<?>> getMappedClasses(Class<?> clazz) {
        if (!clazz.isInterface()) {
            return List.of(clazz);
        }

        List<Class<?>> result = new ArrayList<>();
        result.add(clazz);
        result.addAll(graphQLBeanIntrospectionRegistry.getImplementationClasses(clazz));
        return result;
    }

    private List<ObjectTypeDefinition> getImplementations(InterfaceTypeDefinition interfaceTypeDefinition) {
        return typeDefinitionRegistry.getImplementationsOf(interfaceTypeDefinition).stream()
                .sorted(Comparator.comparing(ObjectTypeDefinition::getName))
                .toList();
    }

    /**
     * Registers the type resolver of every interface that a field returns or that a bound object type implements. Such
     * an implemented-only interface is never the type of a field, so graphql-java never calls its type resolver, but
     * the schema requires one. Each type resolver maps the classes of the bound implementations.
     *
     * @return the interfaces that no field returns, but that a bound object type implements
     */
    private Set<String> wireInterfaceTypeResolvers() {
        Set<String> implementedInterfaces = new TreeSet<>();

        List<InterfaceTypeDefinition> interfaceTypeDefinitions =
                typeDefinitionRegistry.getTypes(InterfaceTypeDefinition.class).stream()
                        .sorted(Comparator.comparing(InterfaceTypeDefinition::getName))
                        .toList();

        for (InterfaceTypeDefinition interfaceTypeDefinition : interfaceTypeDefinitions) {
            String name = interfaceTypeDefinition.getName();
            boolean returned = processedTypes.containsKey(name);

            Map<Class<?>, String> exactClasses = new HashMap<>();
            Map<String, Class<?>> objectTypes = new HashMap<>();
            Set<Class<?>> sharedClasses = new HashSet<>();
            boolean implemented = false;
            for (ObjectTypeDefinition implementation : getImplementations(interfaceTypeDefinition)) {
                Class<?> clazz = getBoundClass(implementation);
                if (clazz != null) {
                    implemented = true;
                    objectTypes.put(implementation.getName(), clazz);
                    for (Class<?> mappedClass : getMappedClasses(clazz)) {
                        if (exactClasses.putIfAbsent(mappedClass, implementation.getName()) != null) {
                            // only possible for an implemented-only interface (the dictionary maps each class once):
                            // its type resolver is never called, and an exact lookup must not pick one of the types
                            sharedClasses.add(mappedClass);
                        }
                    }
                }
            }
            sharedClasses.forEach(exactClasses::remove);

            if (!returned && !implemented) {
                continue;
            }
            if (!returned) {
                implementedInterfaces.add(name);
                manifestRecorder.type(name, "interface", null);
            }
            objectTypes.forEach((objectType, clazz) -> manifestRecorder.resolves(name, objectType, clazz));

            rootRuntimeWiringBuilder.type(name, typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder
                    .typeResolver(new AbstractTypeResolver(graphQLSchemaProvider, "interface", name, exactClasses,
                            objectTypes)));
        }

        return implementedInterfaces;
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
                collectFailure(() -> processFieldDefinition(
                        fieldDefinition, objectTypeDefinition, typeRuntimeWiringBuilder, beanIntrospection
                ));
            }
        });
    }

    private void processFieldDefinition(FieldDefinition fieldDefinition, ObjectTypeDefinition objectTypeDefinition,
                                        TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                        BeanIntrospection<Object> beanIntrospection) {
        TypeMappingContext mappingContext = TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());

        Optional<BeanProperty<Object, Object>> beanProperty =
                beanIntrospection.getProperty(fieldDefinition.getName());
        List<BeanMethod<Object, ?>> beanMethods = beanIntrospection.getBeanMethods().stream()
                .filter(it -> it.getName().equals(fieldDefinition.getName()))
                .collect(Collectors.toList());

        if (beanProperty.isPresent() && !beanMethods.isEmpty()) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanMethods, beanProperty.get()));
        }

        if (beanMethods.size() > 1) {
            throw new MultipleMethodsFoundException(mappingContext, toMap(beanMethods, null));
        }

        List<InterfaceTypeDefinition> declaringInterfaces =
                getDeclaringInterfaces(objectTypeDefinition, fieldDefinition.getName());

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
            // an interface resolver may bind it, see bindInterfaceFields()
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

    /**
     * Binds a field that a member of the Java type of its object type (a property or a {@code @GraphQLField} method)
     * can bind, applying {@link GraphQLToolsConfiguration#getPrecedence()} and
     * {@link GraphQLToolsConfiguration#getResolverDirectives()} when a type resolver method can bind it too. The type
     * resolvers of the interfaces that declare the field are known only after the traversal, so such a field is
     * decided by {@link #bindInterfaceFields()} when a type resolver could win.
     */
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
                throw new AmbiguousBindingException(mappingContext, merge(modelMember.members(), toMap(resolvers)));
            }
            if (directive == null) {
                recordAmbiguity(mappingContext, modelMember, resolvers);
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
            // only an interface resolver can bind it, see bindInterfaceFields()
            unboundInterfaceFields.add(new InterfaceField(objectTypeDefinition, fieldDefinition, sourceClass,
                    modelMember.members(), modelMember));
            return;
        }

        if (!declaringInterfaces.isEmpty() && precedence == Precedence.RESOLVER_FIRST) {
            // an interface resolver binds it if there is one, see bindInterfaceFields()
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

            // the bean property don't have arguments, that's why we only validates arguments count, not exact types
            checkArgumentCount(beanProperty, mappingContext);

            processFieldReturnType(beanProperty.asArgument(), fieldDefinition.getType(), mappingContext);

            manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                    BindingManifestRecorder.propertyMember(beanProperty), beanProperty.asArgument(),
                    BindingManifestRecorder.genericType(beanProperty), false);
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

        manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_PROPERTY,
                modelMember.manifestMember(), beanMethod.getReturnType().asArgument(),
                BindingManifestRecorder.genericReturnType(beanMethod), false);
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
                applicationContext.getBean(beanDefinitionAndMethod.getBeanDefinition()),
                typeRuntimeWiringBuilder,
                mappingContext
        );

        manifestRecorder.field(objectTypeDefinition, fieldDefinition, BindingManifestRecorder.KIND_RESOLVER,
                BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                executable.getReturnType().asArgument(),
                BindingManifestRecorder.genericReturnType(executable), false);
    }

    /**
     * @param fieldDefinition a field of an object type
     * @return the first directive of {@link GraphQLToolsConfiguration#getResolverDirectives()} that the field carries,
     * or {@code null}
     */
    @Nullable
    private String getResolverDirective(FieldDefinition fieldDefinition) {
        for (String directive : configuration.getResolverDirectives()) {
            if (fieldDefinition.hasDirective(directive)) {
                return directive;
            }
        }
        return null;
    }

    /**
     * Checks that every directive listed in {@link GraphQLToolsConfiguration#getResolverDirectives()} is declared by
     * the schema, so that the list cannot go stale.
     */
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

    /**
     * Records a field that both a member of its Java type and a type resolver method can bind, and that
     * {@link GraphQLToolsConfiguration#getPrecedence()} decided.
     */
    private void recordAmbiguity(TypeMappingContext mappingContext, ModelMember modelMember,
                                 List<BeanDefinitionAndMethod> resolvers) {
        String coordinate = mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField();
        ambiguousBindings.put(coordinate, describeMembers(modelMember.members()) + " | "
                + describeMembers(toMap(resolvers)));
        if (configuration.getPrecedence() == Precedence.PROPERTY_FIRST) {
            // the precedence chose the member: the resolver methods are accounted for by the ambiguous line
            resolvers.forEach(it -> shadowedResolverMethods.add(GraphQLResolversRegistry.describeMethod(
                    it.getBeanDefinition(), it.getExecutableMethod())));
        }
        manifestRecorder.diagnostic("ambiguous", coordinate, Stream.concat(
                        Stream.of(modelMember.manifestMember()),
                        resolvers.stream().map(it -> BindingManifestRecorder.methodMember(it.getExecutableMethod(),
                                it.getExecutableMethod().getDeclaringType())).sorted())
                .collect(Collectors.joining(" | ")));
    }

    private static String describeMembers(Map<Class<?>, List<String>> members) {
        return members.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().sorted().map(it -> entry.getKey().getName() + " " + it))
                .collect(Collectors.joining(", "));
    }

    private static Map<Class<?>, List<String>> merge(Map<Class<?>, List<String>> first,
                                                     Map<Class<?>, List<String>> second) {
        Map<Class<?>, List<String>> result = new TreeMap<>(Comparator.comparing(Class::getName));
        first.forEach((clazz, names) -> result.computeIfAbsent(clazz, it -> new ArrayList<>()).addAll(names));
        second.forEach((clazz, names) -> result.computeIfAbsent(clazz, it -> new ArrayList<>()).addAll(names));
        return result;
    }

    /**
     * @param objectTypeDefinition an object type
     * @param fieldName            one of its fields
     * @return the interfaces the object type implements that declare the field, by name
     */
    private List<InterfaceTypeDefinition> getDeclaringInterfaces(ObjectTypeDefinition objectTypeDefinition,
                                                                 String fieldName) {
        return objectTypeDefinition.getImplements().stream()
                .map(it -> typeDefinitionRegistry.getType(getTypeName(it).getName(), InterfaceTypeDefinition.class))
                .flatMap(Optional::stream)
                .filter(it -> it.getFieldDefinitions().stream().anyMatch(field -> field.getName().equals(fieldName)))
                .sorted(Comparator.comparing(InterfaceTypeDefinition::getName))
                .toList();
    }

    /**
     * Finds the type resolver methods for a field declared by the interfaces of an object type: the methods of the
     * {@link io.micronaut.graphql.tools.annotation.GraphQLTypeResolver}s of the Java type each interface is mapped to.
     * Only interfaces that a field returns have a Java type.
     *
     * @param field the interface field of an object type
     * @return the interface resolver methods, possibly none
     */
    private List<InterfaceResolverMethod> findInterfaceResolverMethods(InterfaceField field) {
        List<InterfaceResolverMethod> result = new ArrayList<>();
        String fieldName = field.fieldDefinition().getName();

        for (InterfaceTypeDefinition interfaceTypeDefinition
                : getDeclaringInterfaces(field.objectTypeDefinition(), fieldName)) {
            Class<?> interfaceClass = processedTypes.get(interfaceTypeDefinition.getName());

            // the object type's own type resolvers are already its own binding
            if (interfaceClass == null || interfaceClass.equals(field.ownSourceClass())) {
                continue;
            }

            for (BeanDefinitionAndMethod method
                    : graphQLResolversRegistry.findTypeExecutableMethods(interfaceClass,
                    interfaceTypeDefinition.getName(), fieldName)) {
                result.add(new InterfaceResolverMethod(interfaceTypeDefinition, interfaceClass, method));
            }
        }

        return result;
    }

    /**
     * Binds the interface fields that the class of an implementing object type does not bind itself to the type
     * resolver of the interface, then checks for ambiguities. It runs after the traversal from the root types, so that
     * the Java type of every reached interface is known, and repeats while binding reaches more types. All checks use
     * the final mapping, so the result does not depend on the order of the schema.
     * <ul>
     *     <li>An interface field that the implementation binds itself (property, method or its own type resolver) and
     *     that an interface resolver binds too is ambiguous.</li>
     *     <li>So is an interface field with resolvers of two interfaces.</li>
     *     <li>An interface field that nothing binds is a {@link io.micronaut.graphql.tools.exceptions.MethodNotFoundException},
     *     as for any other field.</li>
     * </ul>
     */
    private void bindInterfaceFields() {
        // binding a resolver over a member (resolver-first) may reach more types, whose fields are bound in turn
        boolean rebound = true;
        while (rebound) {
            rebound = bindInterfaceFieldsOnce();
        }
    }

    /**
     * @return {@code true} if a type resolver replaced a member bound before, so that binding has to run again
     */
    private boolean bindInterfaceFieldsOnce() {
        while (true) {
            boolean progress = false;
            for (InterfaceField field : List.copyOf(unboundInterfaceFields)) {
                List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
                if (methods.isEmpty()) {
                    continue;
                }

                unboundInterfaceFields.remove(field);
                progress = true;

                if (methods.size() > 1) {
                    bindingFailures.add(new MultipleMethodsFoundException(field.mappingContext(),
                            toInterfaceMethodsMap(methods)));
                    continue;
                }

                interfaceResolverFields.add(field);
                collectFailure(() -> bindInterfaceField(field, methods.get(0)));
            }

            if (progress) {
                continue;
            }
            if (deferredModelMemberFields.isEmpty()) {
                break;
            }

            // precedence resolver-first: an interface resolver binds the field if there is one, else the member
            for (InterfaceField field : sortedByCoordinate(deferredModelMemberFields)) {
                deferredModelMemberFields.remove(field);
                List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
                if (methods.isEmpty()) {
                    ownInterfaceFields.add(field);
                    collectFailure(() -> bindModelMember(field.objectTypeDefinition(), field.fieldDefinition(),
                            objectTypeWirings.get(field.objectTypeDefinition().getName()), field.modelMember()));
                } else {
                    collectFailure(() -> bindResolverOverModelMember(field, methods));
                }
            }
        }

        for (InterfaceField field : sortedByCoordinate(unboundInterfaceFields)) {
            unboundInterfaceFields.remove(field);
            if (field.modelMember() != null) {
                bindingFailures.add(new ResolverRequiredException(field.mappingContext(),
                        getResolverDirective(field.fieldDefinition()), field.ownMembers()));
            } else {
                bindingFailures.add(methodNotFound(field.objectTypeDefinition().getName(), field.ownSourceClass(),
                        field.fieldDefinition().getName(), field.mappingContext()));
            }
        }

        for (InterfaceField field : sortedByCoordinate(interfaceResolverFields)) {
            List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
            if (methods.size() > 1) {
                interfaceResolverFields.remove(field);
                bindingFailures.add(new MultipleMethodsFoundException(field.mappingContext(),
                        toInterfaceMethodsMap(methods)));
            }
        }

        boolean rebound = false;
        for (InterfaceField field : sortedByCoordinate(ownInterfaceFields)) {
            List<InterfaceResolverMethod> methods = findInterfaceResolverMethods(field);
            if (methods.isEmpty()) {
                continue;
            }

            if (field.modelMember() == null) {
                // the object type's own type resolver and an interface resolver
                Map<Class<?>, List<String>> members = toInterfaceMethodsMap(methods);
                field.ownMembers().forEach((clazz, names) ->
                        members.computeIfAbsent(clazz, it -> new ArrayList<>()).addAll(names));
                ownInterfaceFields.remove(field);
                bindingFailures.add(new MultipleMethodsFoundException(field.mappingContext(), members));
                continue;
            }

            switch (configuration.getPrecedence()) {
                case STRICT -> {
                    ownInterfaceFields.remove(field);
                    bindingFailures.add(new AmbiguousBindingException(field.mappingContext(),
                            merge(field.ownMembers(), toInterfaceMethodsMap(methods))));
                }
                case PROPERTY_FIRST -> recordAmbiguity(field.mappingContext(), field.modelMember(),
                        methods.stream().map(InterfaceResolverMethod::method).toList());
                case RESOLVER_FIRST -> {
                    // the interface's Java type became known only after the member was bound: bind the resolver
                    ownInterfaceFields.remove(field);
                    collectFailure(() -> bindResolverOverModelMember(field, methods));
                    rebound = true;
                }
                default -> throw new IllegalStateException("Unknown precedence " + configuration.getPrecedence());
            }
        }

        return rebound;
    }

    /**
     * Runs the binding of one field and records a failure instead of stopping at it, so that every field that cannot
     * be bound is reported at once.
     *
     * @param action the binding of one field
     */
    private void collectFailure(Runnable action) {
        try {
            action.run();
        } catch (AbstractMappingException e) {
            bindingFailures.add(e);
        }
    }

    /**
     * Throws the binding failures collected so far: a single one as is, several as a {@link BindingFailuresException},
     * sorted by schema coordinate.
     */
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

    private void bindResolverOverModelMember(InterfaceField field, List<InterfaceResolverMethod> methods) {
        if (methods.size() > 1) {
            throw new MultipleMethodsFoundException(field.mappingContext(), toInterfaceMethodsMap(methods));
        }
        recordAmbiguity(field.mappingContext(), field.modelMember(),
                methods.stream().map(InterfaceResolverMethod::method).toList());
        interfaceResolverFields.add(field);
        bindInterfaceField(field, methods.get(0));
    }

    private void bindInterfaceField(InterfaceField field, InterfaceResolverMethod method) {
        TypeMappingContext mappingContext = field.mappingContext();
        BeanDefinitionAndMethod beanDefinitionAndMethod = method.method();

        String coordinate = field.coordinate();
        String description = GraphQLResolversRegistry.describeMethod(
                beanDefinitionAndMethod.getBeanDefinition(), beanDefinitionAndMethod.getExecutableMethod());
        fieldBindings.put(coordinate, "resolver " + description + " of interface "
                + method.interfaceTypeDefinition().getName());
        resolverBindings.computeIfAbsent(description, it -> new TreeSet<>())
                .add(method.interfaceTypeDefinition().getName() + "." + field.fieldDefinition().getName());

        ExecutableMethod<Object, ?> executable = beanDefinitionAndMethod.getExecutableMethod();

        processExecutableMethod(
                executable,
                executable.getReturnType(),
                method.interfaceClass(),
                applicationContext.getBean(beanDefinitionAndMethod.getBeanDefinition()),
                objectTypeWirings.get(field.objectTypeDefinition().getName()),
                mappingContext
        );

        manifestRecorder.field(field.objectTypeDefinition(), field.fieldDefinition(),
                BindingManifestRecorder.KIND_RESOLVER,
                BindingManifestRecorder.methodMember(executable, executable.getDeclaringType()),
                executable.getReturnType().asArgument(),
                BindingManifestRecorder.genericReturnType(executable), false);
    }

    private static Map<Class<?>, List<String>> toInterfaceMethodsMap(List<InterfaceResolverMethod> methods) {
        return toMap(methods.stream().map(InterfaceResolverMethod::method).toList());
    }

    private static List<InterfaceField> sortedByCoordinate(List<InterfaceField> fields) {
        return fields.stream().sorted(Comparator.comparing(InterfaceField::coordinate)).toList();
    }

    /**
     * @return the {@link MethodNotFoundException} for a field that neither a property, a method of the type nor a type
     * resolver binds; for a type read through the introspection of a Java interface it says that getters only an
     * implementation class declares are not candidates
     */
    private MethodNotFoundException methodNotFound(String objectTypeName, Class<?> sourceClass, String fieldName,
                                                   TypeMappingContext mappingContext) {
        MethodNotFoundException exception =
                graphQLResolversRegistry.typeMethodNotFound(sourceClass, fieldName, mappingContext);
        Class<?> contractClass = contractTypes.get(objectTypeName);

        return contractClass != null ? exception.forContract(contractClass) : exception;
    }

    private void processInputObjectTypeDefinition(InputObjectTypeDefinition inputObjectTypeDefinition,
                                                  Class<?> targetClass, MappingContext mappingContext) {
        processIfNotProcessed(inputObjectTypeDefinition, targetClass, mappingContext, () -> {
            if (targetClass.isInterface()) {
                throw IncorrectClassMappingException.forArgument(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            BeanIntrospection<Object> beanIntrospection = BeanIntrospector.SHARED
                    .findIntrospection((Class<Object>) targetClass)
                    .orElseThrow(() -> new ClassNotIntrospectedException(mappingContext, targetClass, GraphQLInput.class));

            if (
                    beanIntrospection.getBeanType().isInterface()
                            || beanIntrospection.getBeanType().isEnum()
                            || beanIntrospection.getBeanType().isAnnotation()
            ) {
                throw IncorrectClassMappingException.forArgument(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            Set<String> settableProperties = inputClassChecks.findSettableProperties(targetClass);
            if (settableProperties == null) {
                inputClassChecks.addOwnDeserializer(inputObjectTypeDefinition.getName(), targetClass);
            }
            boolean detectOmission = configuration.getInput().isDetectOmission();
            List<BeanProperty<Object, Object>> argumentValueProperties = new ArrayList<>();
            List<BeanProperty<Object, Object>> inputObjectProperties = new ArrayList<>();

            for (InputValueDefinition inputValueDefinition : inputObjectTypeDefinition.getInputValueDefinitions()) {
                Optional<BeanProperty<Object, Object>> property =
                        beanIntrospection.getProperty(inputValueDefinition.getName());

                if (property.isEmpty()) {
                    throw MethodNotFoundException.forInput(
                            inputValueDefinition.getName(),
                            new InputMappingContext(
                                    inputObjectTypeDefinition,
                                    inputValueDefinition.getName(),
                                    targetClass,
                                    null
                            ),
                            beanIntrospection.getBeanType()
                    );
                }

                InputMappingContext inputMappingContext = new InputMappingContext(
                        inputObjectTypeDefinition,
                        inputValueDefinition.getName(),
                        targetClass,
                        property.get().getName()
                );

                processInputType(inputValueDefinition.getType(), property.get().asArgument(), inputMappingContext);

                manifestRecorder.inputField(inputObjectTypeDefinition.getName(), inputValueDefinition.getName(),
                        inputValueDefinition.getType(), beanIntrospection.getBeanType(), property.get());

                if (ArgumentValue.class.equals(property.get().getType()) && property.get().isReadOnly()) {
                    throw new UnsupportedInputTypeException(inputMappingContext, ArgumentValue.class.getName()
                            + " needs a writable property: it is set to omitted() when the client leaves it out.");
                }

                if (settableProperties != null && !settableProperties.contains(inputValueDefinition.getName())) {
                    throw new InputFieldNotSettableException(inputMappingContext);
                }

                if (detectOmission) {
                    if (ArgumentValue.class.equals(property.get().getType())) {
                        if (property.get().isWriteOnly()) {
                            throw new UnsupportedInputTypeException(inputMappingContext, ArgumentValue.class.getName()
                                    + " needs a readable property: it is read to set it to omitted() when the client "
                                    + "leaves it out.");
                        }
                        argumentValueProperties.add(property.get());
                    } else if (isInputObjectType(inputValueDefinition.getType())) {
                        if (property.get().isWriteOnly()) {
                            inputClassChecks.addWriteOnlyInputObjectProperty(inputMappingContext,
                                    InputClassChecks.elementClass(property.get().asArgument()));
                        } else {
                            inputObjectProperties.add(property.get());
                        }
                    }
                }
            }

            if (!argumentValueProperties.isEmpty() || !inputObjectProperties.isEmpty()) {
                omissionPlans.put(targetClass,
                        new InputValueConverter.OmissionPlan(argumentValueProperties, inputObjectProperties));
            }

            inputClassChecks.collectUnmappedInputProperties(inputObjectTypeDefinition, beanIntrospection);

            manifestRecorder.type(inputObjectTypeDefinition.getName(), "input", targetClass);
        });
    }

    private void processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext) {
        processInputType(graphQlType, argument, mappingContext, true);
    }

    private boolean isInputObjectType(Type<?> graphQlType) {
        Type<?> type = graphQlType;
        while (!(type instanceof TypeName)) {
            type = type instanceof NonNullType nonNullType ? nonNullType.getType() : ((ListType) type).getType();
        }
        return typeDefinitionRegistry.getType(((TypeName) type).getName())
                .filter(InputObjectTypeDefinition.class::isInstance)
                .isPresent();
    }

    private void processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext,
                                  boolean fieldLevel) {
        argument = unwrapArgumentValue(unwrapArgument(argument), graphQlType, fieldLevel, mappingContext);
        boolean nonNull = graphQlType instanceof NonNullType;
        graphQlType = unwrapNonNullType(graphQlType);

        Type<?> fieldType = unwrapNonNullType(graphQlType);
        Class<?> returnType = argument.getType();

        if (fieldType instanceof ListType listType) {
            if (!(Iterable.class.isAssignableFrom(returnType))) {
                throw IncorrectClassMappingException.forArgument(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.ITERABLE,
                        mappingContext,
                        returnType,
                        getSupportedClasses(listType)
                );
            }

            Type<?> listFieldType = ((ListType) fieldType).getType();
            Argument<?> listArgument = argument.getFirstTypeVariable().get();

            processInputType(listFieldType, listArgument, mappingContext, false);
            return;
        }

        TypeName typeName = requireTypeName(graphQlType);

        TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(typeName).get();

        if (typeDefinition instanceof InputObjectTypeDefinition inputObjectTypeDefinition) {
            processInputObjectTypeDefinition(
                    inputObjectTypeDefinition,
                    returnType,
                    mappingContext
            );
        } else if (typeDefinition instanceof EnumTypeDefinition enumTypeDefinition) {
            processEnumTypeDefinition(enumTypeDefinition, returnType, true, mappingContext);
        } else if (isGraphQlBuiltInType(typeName)) {
            manifestRecorder.scalar(typeName.getName());
            Set<Class<?>> supportedClasses = getSupportedClasses(typeName);

            if (!supportedClasses.contains(returnType)) {
                throw IncorrectClassMappingException.forArgument(mappingContext, returnType, supportedClasses);
            }
            if (returnType.isPrimitive() && !nonNull && configuration.isStrict()) {
                // null (omitted or explicit) cannot reach a primitive; without strict mode this stays as it was
                throw IncorrectClassMappingException.forNullablePrimitiveInStrictMode(mappingContext, returnType,
                        typeName.getName(), supportedClasses);
            }
        } else if (typeDefinition instanceof ScalarTypeDefinition scalarTypeDefinition) {
            GraphQLScalarMapping scalarMapping = requireScalarMapping(scalarTypeDefinition, mappingContext);

            if (scalarMapping.getInputClasses().isEmpty()) {
                throw IncorrectClassMappingException.forScalarWithoutClasses(mappingContext, returnType,
                        scalarMapping.getName(), true);
            }
            if (!scalarMapping.isInputClass(returnType)) {
                throw IncorrectClassMappingException.forArgument(mappingContext, returnType,
                        scalarMapping.getInputClasses());
            }
            if (returnType.isPrimitive() && !nonNull) {
                // null (omitted or explicit) cannot be passed to a primitive parameter or set on a primitive property
                throw IncorrectClassMappingException.forNullablePrimitive(mappingContext, returnType,
                        scalarMapping.getName(), scalarMapping.getInputClasses());
            }
        } else {
            throw unsupportedTypeDefinition(typeDefinition);
        }
    }

    /**
     * Removes an {@link ArgumentValue} from the declared type of an argument or an input class property. It needs
     * {@code input.detect-omission}, a nullable GraphQL type (only those can be omitted or {@code null}) and the field
     * level (a list element cannot be omitted).
     *
     * @param argument       the declared type
     * @param graphQlType    the GraphQL type of the argument, input field or list element, before removing non-null
     * @param fieldLevel     {@code true} for the argument or input field itself, {@code false} for a list element
     * @param mappingContext the mapping context
     * @return the type the GraphQL type has to be mapped to
     */
    private Argument<?> unwrapArgumentValue(Argument<?> argument, Type<?> graphQlType, boolean fieldLevel,
                                            MappingContext mappingContext) {
        if (!ArgumentValue.class.equals(argument.getType())) {
            return argument;
        }
        String wrapper = ArgumentValue.class.getName();
        if (!configuration.getInput().isDetectOmission()) {
            throw new UnsupportedInputTypeException(mappingContext,
                    wrapper + " needs " + GraphQLToolsConfiguration.PREFIX + ".input.detect-omission=true.");
        }
        if (!fieldLevel) {
            throw new UnsupportedInputTypeException(mappingContext,
                    wrapper + " is not allowed inside a list: a list element cannot be omitted.");
        }
        if (graphQlType instanceof NonNullType) {
            throw new UnsupportedInputTypeException(mappingContext, wrapper
                    + " is only allowed for a nullable GraphQL type, the type is " + printType(graphQlType) + ".");
        }
        Argument<?> result = argument.getFirstTypeVariable()
                .orElseThrow(() -> new UnsupportedInputTypeException(mappingContext,
                        wrapper + " must declare the type it wraps."));
        if (ArgumentValue.class.equals(result.getType())) {
            throw new UnsupportedInputTypeException(mappingContext, wrapper + " cannot wrap " + wrapper + ".");
        }
        return result;
    }

    private GraphQLScalarMapping requireScalarMapping(ScalarTypeDefinition scalarTypeDefinition,
                                                      MappingContext mappingContext) {
        GraphQLScalarMapping scalarMapping = scalarMappings.get(scalarTypeDefinition.getName());
        if (scalarMapping == null) {
            throw new ScalarMappingNotProvidedException(mappingContext, scalarTypeDefinition.getName());
        }
        manifestRecorder.scalar(scalarTypeDefinition.getName());
        return scalarMapping;
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

            // do nothing as it already processed
            return;
        }

        // save processed type before actual processing is because sometimes one type can refer to itself in it fields
        processedTypes.put(typeDefinition.getName(), targetClass);

        runnable.run();
    }

    /**
     * Two Java types bind a GraphQL object type alike when they are equal, or when one is a Java interface and the
     * other a class annotated with {@code @GraphQLType(thatInterface)}, or both are such classes, and the type's
     * properties are read through the same bean introspection either way.
     */
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

    /**
     * A field of an object type that one of its interfaces declares.
     *
     * @param objectTypeDefinition the object type
     * @param fieldDefinition      the field
     * @param ownSourceClass       the class the object type's own type resolvers are declared for
     * @param ownMembers           the members of the object type's class or type resolvers that bind the field
     * @param modelMember          the member of the object type's class that binds or may bind the field, or
     *                             {@code null} if its own type resolver binds it or nothing does
     */
    private record InterfaceField(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                                  Class<?> ownSourceClass,
                                  Map<Class<?>, List<String>> ownMembers,
                                  @Nullable ModelMember modelMember) {

        String coordinate() {
            return objectTypeDefinition.getName() + "." + fieldDefinition.getName();
        }

        TypeMappingContext mappingContext() {
            return TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());
        }

    }

    /**
     * A type resolver method of the Java type an interface is mapped to.
     *
     * @param interfaceTypeDefinition the interface
     * @param interfaceClass          the Java type the interface is mapped to
     * @param method                  the type resolver method
     */
    private record InterfaceResolverMethod(InterfaceTypeDefinition interfaceTypeDefinition, Class<?> interfaceClass,
                                           BeanDefinitionAndMethod method) {
    }

    /**
     * A member of the Java type of an object type that can bind a field: a bean property or a {@code @GraphQLField}
     * method, never both.
     *
     * @param beanIntrospection the introspection the member belongs to
     * @param beanProperty      the property, or {@code null}
     * @param beanMethod        the method, or {@code null}
     */
    private record ModelMember(BeanIntrospection<Object> beanIntrospection,
                               @Nullable BeanProperty<Object, Object> beanProperty,
                               @Nullable BeanMethod<Object, ?> beanMethod) {

        Map<Class<?>, List<String>> members() {
            return beanProperty != null
                    ? toMap(Collections.emptyList(), beanProperty)
                    : toMap(List.of(beanMethod), null);
        }

        String manifestMember() {
            return beanProperty != null
                    ? BindingManifestRecorder.propertyMember(beanProperty)
                    : BindingManifestRecorder.introspectedMethodMember(beanMethod,
                    beanMethod.getDeclaringBean().getBeanType());
        }

    }

}
