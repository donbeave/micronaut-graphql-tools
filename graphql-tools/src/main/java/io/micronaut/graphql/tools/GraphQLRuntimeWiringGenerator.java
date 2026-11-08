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
import graphql.execution.DataFetcherResult;
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
import io.micronaut.graphql.tools.GraphQLResolversRegistry.SchemaCoordinateMethod;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration.Precedence;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException;
import io.micronaut.graphql.tools.exceptions.AmbiguousTypeMappingException;
import io.micronaut.graphql.tools.exceptions.BindingFailuresException;
import io.micronaut.graphql.tools.exceptions.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException;
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException;
import io.micronaut.graphql.tools.exceptions.IncorrectMemberMappingException;
import io.micronaut.graphql.tools.exceptions.InvalidAllowUnreachableException;
import io.micronaut.graphql.tools.exceptions.InterfaceTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException;
import io.micronaut.graphql.tools.exceptions.InvalidSourceArgumentException;
import io.micronaut.graphql.tools.exceptions.MappingConflictException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.exceptions.MissingEnumValuesException;
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException;
import io.micronaut.graphql.tools.exceptions.ResolverRequiredException;
import io.micronaut.graphql.tools.exceptions.RootResolverNotFoundException;
import io.micronaut.graphql.tools.exceptions.SchemaDefinitionNotProvidedException;
import io.micronaut.graphql.tools.exceptions.UnionTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException;
import io.micronaut.graphql.tools.exceptions.UnsupportedReturnTypeException;
import io.micronaut.graphql.tools.schema.AbstractTypeResolver;
import io.micronaut.graphql.tools.schema.DefaultWiringFactory;
import io.micronaut.graphql.tools.schema.MicronautExecutableMethodDataFetcher;
import io.micronaut.graphql.tools.schema.MicronautIntrospectionDataFetcher;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.jackson.modules.BeanIntrospectionModule;
import jakarta.inject.Provider;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;
import static io.micronaut.graphql.tools.GraphQLUtils.getType;
import static io.micronaut.graphql.tools.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.GraphQLUtils.printType;
import static io.micronaut.graphql.tools.GraphQLUtils.requireTypeName;
import static io.micronaut.graphql.tools.GraphQLUtils.unsupportedTypeDefinition;
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

    private static final List<Class<?>> RETURN_TYPE_WRAPPERS =
            List.of(CompletionStage.class, Publisher.class, DataFetcherResult.class, Optional.class);

    private static final Pattern SCHEMA_COORDINATE = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*\\.[_A-Za-z][_0-9A-Za-z]*");

    private final ApplicationContext applicationContext;
    private final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final SchemaMappingDictionary schemaMappingDictionary;
    private final Provider<GraphQLSchema> graphQLSchemaProvider;
    private final ObjectMapper objectMapper;
    private final GraphQLToolsConfiguration configuration;
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

    // failures of fields that cannot be bound, reported together once every field was tried
    private final List<AbstractMappingException> bindingFailures = new ArrayList<>();

    // key: schema coordinate (Type.field) that a member and a type resolver can both bind, decided by the precedence,
    // value: the members
    private final Map<String, String> ambiguousBindings = new TreeMap<>();

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

        this.objectMapper = new ObjectMapper()
                .registerModule(new BeanIntrospectionModule());
        this.rootRuntimeWiringBuilder = RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.GraphQLShort)
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.GraphQLBigInteger);
    }

    RuntimeWiring generate() {
        long start = System.nanoTime();

        try {
            SchemaDefinition schemaDefinition = typeDefinitionRegistry.schemaDefinition()
                    .orElseThrow(SchemaDefinitionNotProvidedException::new);

            if (!graphQLResolversRegistry.hasRootResolvers()) {
                throw new RootResolverNotFoundException();
            }

            graphQLResolversRegistry.verifyTypeResolverContracts();

            validateSchemaCoordinates(schemaDefinition);

            validateSchemaMappingDictionary();

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

            if (!ambiguousBindings.isEmpty()) {
                LOG.warn("{} is {}: fields that both a member of the Java type and a type resolver method can bind: {}",
                        GraphQLToolsConfiguration.PREFIX + ".precedence", configuration.getPrecedence(),
                        ambiguousBindings.entrySet().stream()
                                .map(it -> it.getKey() + " (" + it.getValue() + ")")
                                .collect(Collectors.joining("; ")));
            }

            objectTypeWirings.values().forEach(it -> rootRuntimeWiringBuilder.type(it.build()));

            Set<String> implementedInterfaces = wireInterfaceTypeResolvers();

            Set<String> unboundTypes = processUnreachableTypes(schemaDefinition, implementedInterfaces);

            verifySchemaCoordinateBindings(unboundTypes);

            rootRuntimeWiringBuilder.wiringFactory(new DefaultWiringFactory(unboundTypes));

            return rootRuntimeWiringBuilder.build();
        } finally {
            if (LOG.isInfoEnabled()) {
                long took = elapsedMillis(start);
                LOG.info("GraphQL runtime wiring generated in {}ms.", took);
            }
        }
    }

    /**
     * Finds the object, interface and union types of the schema that no field reachable from a root operation type
     * returns (nothing binds them) and applies {@link GraphQLToolsConfiguration#getUnreachableTypes()} and
     * {@link GraphQLToolsConfiguration#getAllowUnreachable()} to them. An interface that no field returns, but that a
     * reached object type implements, is reached.
     *
     * @param schemaDefinition      the schema definition
     * @param implementedInterfaces the interfaces that no field returns, but that a reached object type implements
     * @return the unreachable object types that stay in the schema with graphql-java's default data fetchers
     */
    private Set<String> processUnreachableTypes(SchemaDefinition schemaDefinition, Set<String> implementedInterfaces) {
        Set<String> reachedTypeNames = new HashSet<>(processedTypes.keySet());
        reachedTypeNames.addAll(implementedInterfaces);
        for (OperationTypeDefinition operationTypeDefinition : schemaDefinition.getOperationTypeDefinitions()) {
            reachedTypeNames.add(operationTypeDefinition.getTypeName().getName());
        }

        Map<String, TypeDefinition<?>> unreachableTypes = new TreeMap<>();
        for (TypeDefinition<?> typeDefinition : typeDefinitionRegistry.types().values()) {
            boolean needsBinding = typeDefinition instanceof ObjectTypeDefinition
                    || typeDefinition instanceof InterfaceTypeDefinition
                    || typeDefinition instanceof UnionTypeDefinition;
            if (needsBinding && !reachedTypeNames.contains(typeDefinition.getName())) {
                unreachableTypes.put(typeDefinition.getName(), typeDefinition);
            }
        }

        Set<String> allowed = new TreeSet<>(configuration.getAllowUnreachable());
        List<String> undefined = allowed.stream()
                .filter(it -> !isDefined(it))
                .toList();
        List<String> notUnreachable = allowed.stream()
                .filter(it -> isDefined(it) && !unreachableTypes.containsKey(it))
                .toList();
        if (!undefined.isEmpty() || !notUnreachable.isEmpty()) {
            throw new InvalidAllowUnreachableException(undefined, notUnreachable);
        }

        Set<String> unbound;
        if (configuration.getUnreachableTypes() == GraphQLToolsConfiguration.UnreachableTypes.IGNORE) {
            unbound = unreachableTypes.keySet();
            if (!unbound.isEmpty()) {
                LOG.warn("{} is ignore: the schema defines types that no field reachable from a root operation "
                                + "type returns; they use graphql-java's default data fetchers: {}",
                        GraphQLToolsConfiguration.PREFIX + ".unreachable-types", String.join(", ", unbound));
            }
        } else {
            Map<String, String> failing = new LinkedHashMap<>();
            unreachableTypes.forEach((name, typeDefinition) -> {
                if (!allowed.contains(name)) {
                    failing.put(name, getType(typeDefinition));
                }
            });
            if (!failing.isEmpty()) {
                throw new UnreachableTypesException(failing);
            }
            unbound = allowed;
            if (!unbound.isEmpty()) {
                LOG.info("Unreachable types allowed by {} use graphql-java's default data fetchers: {}",
                        GraphQLToolsConfiguration.PREFIX + ".allow-unreachable", String.join(", ", unbound));
            }
        }

        for (String name : unbound) {
            TypeDefinition<?> typeDefinition = unreachableTypes.get(name);
            if (!(typeDefinition instanceof ObjectTypeDefinition)) {
                // nothing returns an unreachable interface or union, so its type resolver is never called
                rootRuntimeWiringBuilder.type(name, typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder
                        .typeResolver(env -> {
                            throw new IllegalStateException("No type resolver for the unreachable type " + name);
                        }));
            }
        }

        return unbound;
    }

    /**
     * Checks every {@link io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate} against the schema, its method
     * and its bean, before anything is bound.
     *
     * @param schemaDefinition the schema definition
     */
    private void validateSchemaCoordinates(SchemaDefinition schemaDefinition) {
        Set<String> operationTypeNames = schemaDefinition.getOperationTypeDefinitions().stream()
                .map(it -> it.getTypeName().getName())
                .collect(Collectors.toCollection(TreeSet::new));

        List<String> problems = new ArrayList<>();
        for (SchemaCoordinateMethod coordinateMethod : graphQLResolversRegistry.getSchemaCoordinateMethods()) {
            String problem = checkSchemaCoordinate(coordinateMethod, operationTypeNames);
            if (problem != null) {
                problems.add(describe(coordinateMethod) + ": " + problem);
            }
        }

        if (!problems.isEmpty()) {
            throw new InvalidSchemaCoordinateException(problems);
        }
    }

    /**
     * Rejects dictionary entries that map one GraphQL object type to a Java interface and another one to a class or
     * interface that implements it: an object of that class would match both types. Entries for names that are not
     * object types of the schema are left to the code that uses them.
     */
    private void validateSchemaMappingDictionary() {
        List<Map.Entry<String, Class<?>>> entries = schemaMappingDictionary.getTypes().entrySet().stream()
                .filter(it -> typeDefinitionRegistry.getType(it.getKey())
                        .filter(ObjectTypeDefinition.class::isInstance)
                        .isPresent())
                .sorted(Map.Entry.comparingByKey())
                .toList();

        List<String> conflicts = new ArrayList<>();
        Set<String> graphQlTypes = new TreeSet<>();

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Map.Entry<String, Class<?>> first = entries.get(i);
                Map.Entry<String, Class<?>> second = entries.get(j);

                String conflict = describeAmbiguity(first, second);
                if (conflict == null) {
                    conflict = describeAmbiguity(second, first);
                }
                if (conflict != null) {
                    conflicts.add(conflict);
                    graphQlTypes.add(first.getKey());
                    graphQlTypes.add(second.getKey());
                }
            }
        }

        if (!conflicts.isEmpty()) {
            throw new AmbiguousTypeMappingException(conflicts, new ArrayList<>(graphQlTypes));
        }
    }

    @Nullable
    private String checkSchemaCoordinate(SchemaCoordinateMethod coordinateMethod, Set<String> operationTypeNames) {
        BeanDefinition<?> beanDefinition = coordinateMethod.beanDefinition();
        boolean rootResolver = beanDefinition.hasStereotype(GraphQLRootResolver.class);
        boolean typeResolver = beanDefinition.hasStereotype(GraphQLTypeResolver.class);

        if (!rootResolver && !typeResolver) {
            return "the bean is neither a @" + GraphQLRootResolver.class.getSimpleName() + " nor a @"
                    + GraphQLTypeResolver.class.getSimpleName() + ".";
        }

        String coordinate = coordinateMethod.coordinate();
        if (!SCHEMA_COORDINATE.matcher(coordinate).matches()) {
            return "the value is not a schema coordinate of the form Type.field.";
        }

        String typeName = coordinate.substring(0, coordinate.indexOf('.'));
        String fieldName = coordinate.substring(coordinate.indexOf('.') + 1);

        Optional<TypeDefinition> typeDefinition = typeDefinitionRegistry.getType(typeName);
        if (typeDefinition.isEmpty()) {
            return "the schema has no type " + typeName + ".";
        }
        List<FieldDefinition> fieldDefinitions;
        if (typeDefinition.get() instanceof ObjectTypeDefinition objectTypeDefinition) {
            fieldDefinitions = objectTypeDefinition.getFieldDefinitions();
        } else if (typeDefinition.get() instanceof InterfaceTypeDefinition interfaceTypeDefinition) {
            fieldDefinitions = interfaceTypeDefinition.getFieldDefinitions();
        } else {
            return typeName + " is neither an object type nor an interface.";
        }
        if (fieldDefinitions.stream().noneMatch(it -> it.getName().equals(fieldName))) {
            return "the type " + typeName + " has no field " + fieldName + ".";
        }

        String methodName = coordinateMethod.method().getMethodName();
        if (!fieldName.equals(methodName)) {
            return "the field name " + fieldName + " differs from the method name " + methodName + ".";
        }

        boolean operationType = operationTypeNames.contains(typeName);
        if (rootResolver && !operationType) {
            return "a @" + GraphQLRootResolver.class.getSimpleName() + " binds fields of the root operation types "
                    + operationTypeNames + " only, not of " + typeName + ".";
        }
        if (typeResolver && operationType) {
            return "a @" + GraphQLTypeResolver.class.getSimpleName() + " cannot bind a field of the root operation type "
                    + typeName + ".";
        }

        return null;
    }

    /**
     * Checks that every method that carries {@link io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate} is
     * bound to exactly the field its coordinate names. A method whose type is unbound on purpose
     * ({@link GraphQLToolsConfiguration#getAllowUnreachable()}, {@link GraphQLToolsConfiguration#getUnreachableTypes()})
     * is not bound and is logged.
     *
     * @param unboundTypes the types left unbound on purpose
     */
    private void verifySchemaCoordinateBindings(Set<String> unboundTypes) {
        List<String> problems = new ArrayList<>();
        List<String> unboundMethods = new ArrayList<>();

        for (SchemaCoordinateMethod coordinateMethod : graphQLResolversRegistry.getSchemaCoordinateMethods()) {
            String coordinate = coordinateMethod.coordinate();
            String typeName = coordinate.substring(0, coordinate.indexOf('.'));
            Set<String> bound = resolverBindings.getOrDefault(coordinateMethod.describe(), Collections.emptySet());

            if (bound.equals(Set.of(coordinate))) {
                continue;
            }

            if (bound.isEmpty() && unboundTypes.contains(typeName)) {
                unboundMethods.add(describe(coordinateMethod));
                continue;
            }

            Set<String> others = new TreeSet<>(bound);
            others.remove(coordinate);

            if (!others.isEmpty()) {
                problems.add(describe(coordinateMethod) + ": the method is "
                        + (bound.contains(coordinate) ? "also " : "")
                        + "bound to " + String.join(", ", others) + ".");
            } else {
                problems.add(describe(coordinateMethod) + ": the method is not bound to " + coordinate + ", "
                        + describeBinding(coordinateMethod, typeName) + ".");
            }
        }

        if (!problems.isEmpty()) {
            throw new InvalidSchemaCoordinateException(problems);
        }

        if (!unboundMethods.isEmpty()) {
            LOG.warn("Methods whose @GraphQLSchemaCoordinate names an unreachable type are not bound: {}",
                    String.join("; ", unboundMethods));
        }
    }

    private String describeBinding(SchemaCoordinateMethod coordinateMethod, String typeName) {
        String coordinate = coordinateMethod.coordinate();
        String boundTo = fieldBindings.get(coordinate);
        Class<?> mappedClass = processedTypes.get(typeName);

        StringBuilder builder = new StringBuilder(boundTo != null
                ? coordinate + " is bound to " + boundTo
                : "nothing is bound to " + coordinate);

        Optional<Class> resolvedClass = coordinateMethod.beanDefinition()
                .classValue(GraphQLTypeResolver.class);
        if (mappedClass != null && resolvedClass.isPresent()) {
            Class<?> sourceClass = graphQLBeanIntrospectionRegistry.getInterfaceClass(mappedClass);
            if (!sourceClass.equals(resolvedClass.get())) {
                builder.append(" (").append(typeName).append(" is mapped to ").append(sourceClass.getName())
                        .append(", the bean resolves ").append(resolvedClass.get().getName()).append(")");
            }
        }

        return builder.toString();
    }

    private static String describe(SchemaCoordinateMethod coordinateMethod) {
        return "@GraphQLSchemaCoordinate(\"" + coordinateMethod.coordinate() + "\") on " + coordinateMethod.describe();
    }

    private void recordResolverBinding(TypeMappingContext mappingContext,
                                       BeanDefinitionAndMethod beanDefinitionAndMethod) {
        String coordinate = mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField();
        String method = GraphQLResolversRegistry.describeMethod(
                beanDefinitionAndMethod.getBeanDefinition(), beanDefinitionAndMethod.getExecutableMethod());

        fieldBindings.put(coordinate, "resolver " + method);
        resolverBindings.computeIfAbsent(method, it -> new TreeSet<>()).add(coordinate);
    }

    @Nullable
    private String describeAmbiguity(Map.Entry<String, Class<?>> interfaceEntry, Map.Entry<String, Class<?>> other) {
        Class<?> interfaceClass = interfaceEntry.getValue();
        Class<?> otherClass = other.getValue();

        if (!interfaceClass.isInterface() || !interfaceClass.isAssignableFrom(otherClass)) {
            return null;
        }

        return "`" + interfaceEntry.getKey() + "` is mapped to the interface " + interfaceClass.getName() + " and `"
                + other.getKey() + "` to " + otherClass.getName() + ", which implements it";
    }

    private boolean isDefined(String typeName) {
        return typeDefinitionRegistry.types().containsKey(typeName)
                || typeDefinitionRegistry.scalars().containsKey(typeName);
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
                new MicronautExecutableMethodDataFetcher(objectMapper, executable, argumentDefinitions, instance)
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
                .filter(it -> !it.getType().isAssignableFrom(DataFetchingEnvironment.class))
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
                    unwrapNonNullType(inputValueDefinition.getType()),
                    argument,
                    TypeMappingContext.forArgument(mappingContext, inputValueDefinition.getName())
            );

            result.add(ArgumentDefinition.ofInputValueArgument(inputs.get(i).getName(), argument.getType()));
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
        argument = unwrapReturnType(argument, graphQlType, fieldLevel, subscription, mappingContext);
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
            } else {
                throw unsupportedTypeDefinition(typeDefinition);
            }
        }
    }

    /**
     * Removes the wrappers graphql-java resolves at runtime from a declared return type, in the order graphql-java
     * resolves them: {@link CompletionStage} (field level only, graphql-java does not await list elements), then
     * {@link Publisher} (required on fields of the subscription type and allowed nowhere else; the rest applies to each
     * event), then {@link DataFetcherResult} (its data, errors and local context are applied), then {@link Optional}
     * (an empty one becomes {@code null}, so it needs a nullable GraphQL type). Each wrapper may appear at most once.
     * This is stricter than graphql-java in one place: {@code DataFetcherResult<Publisher<T>>} on a subscription field is
     * rejected (graphql-java would unwrap it), so a subscription field returns the {@link Publisher} directly.
     *
     * @param argument       the declared return type
     * @param graphQlType    the GraphQL type of the field or the list element, before removing non-null
     * @param fieldLevel     {@code true} for the field type itself, {@code false} for a list element
     * @param subscription   {@code true} for a field of the subscription type
     * @param mappingContext the mapping context
     * @return the type the GraphQL type has to be mapped to
     */
    private static Argument<?> unwrapReturnType(Argument<?> argument, Type<?> graphQlType, boolean fieldLevel,
                                                boolean subscription, TypeMappingContext mappingContext) {
        Argument<?> result = argument;
        Class<?> outer = null;

        if (CompletionStage.class.isAssignableFrom(result.getType())) {
            if (!fieldLevel) {
                throw new UnsupportedReturnTypeException(mappingContext,
                        CompletionStage.class.getName() + " is not allowed inside a list.");
            }
            outer = CompletionStage.class;
            result = typeArgument(result, CompletionStage.class, mappingContext);
        }

        boolean publisher = Publisher.class.isAssignableFrom(result.getType());

        if (fieldLevel && subscription) {
            if (!publisher) {
                throw new UnsupportedReturnTypeException(mappingContext, "a field of the subscription type must return "
                        + Publisher.class.getName() + " (optionally inside a CompletionStage), found "
                        + result.getType().getName() + ".");
            }
            outer = Publisher.class;
            result = typeArgument(result, Publisher.class, mappingContext);
        } else if (publisher) {
            throw new UnsupportedReturnTypeException(mappingContext,
                    Publisher.class.getName() + " is only allowed on fields of the subscription type.");
        }

        if (DataFetcherResult.class.isAssignableFrom(result.getType())) {
            outer = DataFetcherResult.class;
            result = typeArgument(result, DataFetcherResult.class, mappingContext);
        }

        if (Optional.class.equals(result.getType())) {
            if (graphQlType instanceof NonNullType) {
                throw new UnsupportedReturnTypeException(mappingContext, Optional.class.getName()
                        + " is only allowed for a nullable GraphQL type, the field type is "
                        + printType(graphQlType) + ".");
            }
            outer = Optional.class;
            result = typeArgument(result, Optional.class, mappingContext);
        }

        if (outer != null) {
            Class<?> inner = result.getType();

            for (Class<?> wrapper : RETURN_TYPE_WRAPPERS) {
                if (wrapper.isAssignableFrom(inner)) {
                    throw new UnsupportedReturnTypeException(mappingContext, outer.getName() + " cannot wrap "
                            + wrapper.getName() + ". Supported wrappers, outermost first: CompletionStage "
                            + "(field level only), Publisher (subscription fields only), DataFetcherResult, Optional "
                            + "(nullable types only), each at most once.");
                }
            }
        }

        return result;
    }

    private static Argument<?> typeArgument(Argument<?> argument, Class<?> wrapper,
                                            TypeMappingContext mappingContext) {
        return argument.getFirstTypeVariable()
                .orElseThrow(() -> new UnsupportedReturnTypeException(mappingContext,
                        wrapper.getName() + " must declare the type it wraps."));
    }

    private void processOperationTypeDefinition(OperationTypeDefinition operationTypeDefinition) {
        ObjectTypeDefinition objectTypeDefinition = typeDefinitionRegistry
                .getType(operationTypeDefinition.getTypeName(), ObjectTypeDefinition.class).get();

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
            }

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
            }

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
                recordAmbiguity(mappingContext, modelMember.members(), toMap(resolvers));
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
    private void recordAmbiguity(TypeMappingContext mappingContext, Map<Class<?>, List<String>> modelMembers,
                                 Map<Class<?>, List<String>> resolvers) {
        String coordinate = mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField();
        ambiguousBindings.put(coordinate, describeMembers(modelMembers) + " | " + describeMembers(resolvers));
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
                case PROPERTY_FIRST -> recordAmbiguity(field.mappingContext(), field.ownMembers(),
                        toInterfaceMethodsMap(methods));
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
        recordAmbiguity(field.mappingContext(), field.ownMembers(), toInterfaceMethodsMap(methods));
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

                processInputType(
                        inputValueDefinition.getType(),
                        property.get().asArgument(),
                        new InputMappingContext(
                                inputObjectTypeDefinition,
                                inputValueDefinition.getName(),
                                targetClass,
                                property.get().getName()
                        )
                );
            }
        });
    }

    private void processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext) {
        graphQlType = unwrapNonNullType(graphQlType);
        argument = unwrapArgument(argument);

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

            processInputType(listFieldType, listArgument, mappingContext);
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
            Set<Class<?>> supportedClasses = getSupportedClasses(typeName);

            if (!supportedClasses.contains(returnType)) {
                throw IncorrectClassMappingException.forArgument(mappingContext, returnType, supportedClasses);
            }
        } else {
            throw unsupportedTypeDefinition(typeDefinition);
        }
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

    }

}
