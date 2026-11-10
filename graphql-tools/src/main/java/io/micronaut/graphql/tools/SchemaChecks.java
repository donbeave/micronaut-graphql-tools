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

import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.GraphQLResolversRegistry.SchemaCoordinateMethod;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.exceptions.AmbiguousTypeMappingException;
import io.micronaut.graphql.tools.exceptions.InvalidAllowUnreachableException;
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException;
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException;
import io.micronaut.inject.BeanDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.GraphQLUtils.getType;

/**
 * The checks of {@link GraphQLRuntimeWiringGenerator} that look at the schema as a whole rather than at one binding:
 * schema coordinates and dictionary entries before anything is bound, and unreachable types and the bindings of
 * schema coordinates afterwards. They log under the generator's name.
 */
@Internal
final class SchemaChecks {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private static final Pattern SCHEMA_COORDINATE = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*\\.[_A-Za-z][_0-9A-Za-z]*");

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    private final SchemaMappingDictionary schemaMappingDictionary;
    private final GraphQLToolsConfiguration configuration;
    private final BindingManifestRecorder manifestRecorder;

    SchemaChecks(TypeDefinitionRegistry typeDefinitionRegistry,
                 GraphQLResolversRegistry graphQLResolversRegistry,
                 GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry,
                 SchemaMappingDictionary schemaMappingDictionary,
                 GraphQLToolsConfiguration configuration,
                 BindingManifestRecorder manifestRecorder) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.graphQLBeanIntrospectionRegistry = graphQLBeanIntrospectionRegistry;
        this.schemaMappingDictionary = schemaMappingDictionary;
        this.configuration = configuration;
        this.manifestRecorder = manifestRecorder;
    }

    /**
     * Finds the object, interface and union types of the schema that no field reachable from a root operation type
     * returns (nothing binds them) and applies {@link GraphQLToolsConfiguration#getUnreachableTypes()} and
     * {@link GraphQLToolsConfiguration#getAllowUnreachable()} to them. An interface that no field returns, but that a
     * reached object type implements, is reached.
     *
     * @param schemaDefinition      the schema definition
     * @param implementedInterfaces the interfaces that no field returns, but that a reached object type implements
     * @param processedTypes        the types bound so far, by name
     * @param runtimeWiringBuilder  the runtime wiring, which gets a type resolver for unbound abstract types
     * @return the unreachable object types that stay in the schema with graphql-java's default data fetchers
     */
    Set<String> processUnreachableTypes(SchemaDefinition schemaDefinition, Set<String> implementedInterfaces,
                                        Map<String, Class<?>> processedTypes,
                                        RuntimeWiring.Builder runtimeWiringBuilder) {
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

        // enum, input and scalar types: reported in the manifest, and handled like the others in strict mode
        Map<String, TypeDefinition<?>> unreachableValueTypes = findUnreachableValueTypes(processedTypes);
        boolean strict = configuration.isStrict();
        Map<String, TypeDefinition<?>> checked = new TreeMap<>(unreachableTypes);
        if (strict) {
            checked.putAll(unreachableValueTypes);
        }

        Set<String> allowed = new TreeSet<>(configuration.getAllowUnreachable());
        List<String> undefined = allowed.stream()
                .filter(it -> !isDefined(it))
                .toList();
        List<String> notUnreachable = allowed.stream()
                // an unreachable enum, input or scalar type may be listed without strict mode too, without effect
                .filter(it -> isDefined(it) && !unreachableTypes.containsKey(it)
                        && !unreachableValueTypes.containsKey(it))
                .toList();
        if (!undefined.isEmpty() || !notUnreachable.isEmpty()) {
            throw new InvalidAllowUnreachableException(undefined, notUnreachable,
                    "object, interface, union, enum, input or scalar");
        }

        Set<String> unbound;
        if (configuration.getUnreachableTypes() == GraphQLToolsConfiguration.UnreachableTypes.IGNORE) {
            unbound = unreachableTypes.keySet();
            if (!checked.isEmpty()) {
                LOG.warn("{} is ignore: the schema defines types that no field reachable from a root operation "
                                + "type returns; they use graphql-java's default data fetchers: {}",
                        GraphQLToolsConfiguration.PREFIX + ".unreachable-types", String.join(", ", checked.keySet()));
            }
        } else {
            Map<String, String> failing = new LinkedHashMap<>();
            checked.forEach((name, typeDefinition) -> {
                if (!allowed.contains(name)) {
                    failing.put(name, getType(typeDefinition));
                }
            });
            if (!failing.isEmpty()) {
                throw new UnreachableTypesException(failing);
            }
            unbound = allowed.stream().filter(unreachableTypes::containsKey)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (!allowed.isEmpty()) {
                LOG.info("Unreachable types allowed by {} use graphql-java's default data fetchers: {}",
                        GraphQLToolsConfiguration.PREFIX + ".allow-unreachable", String.join(", ", allowed));
            }
        }

        unbound.forEach(it -> manifestRecorder.diagnostic("unreachable", it));
        unreachableValueTypes.keySet().forEach(it -> manifestRecorder.diagnostic("unreachable", it));

        for (String name : unbound) {
            TypeDefinition<?> typeDefinition = unreachableTypes.get(name);
            if (!(typeDefinition instanceof ObjectTypeDefinition)) {
                // nothing returns an unreachable interface or union, so its type resolver is never called
                runtimeWiringBuilder.type(name, typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder
                        .typeResolver(env -> {
                            throw new IllegalStateException("No type resolver for the unreachable type " + name);
                        }));
            }
        }

        return unbound;
    }

    /**
     * @return the enum, input object and scalar types the schema declares that no bound field, argument or input field
     * and no argument of a directive definition uses, by name; the scalars graphql-java defines are not declared
     */
    private Map<String, TypeDefinition<?>> findUnreachableValueTypes(Map<String, Class<?>> processedTypes) {
        Set<String> directiveTypes = GraphQLUtils.findDirectiveArgumentTypes(typeDefinitionRegistry);
        Map<String, TypeDefinition<?>> result = new TreeMap<>();
        for (TypeDefinition<?> typeDefinition : typeDefinitionRegistry.types().values()) {
            boolean valueType = typeDefinition instanceof EnumTypeDefinition
                    || typeDefinition instanceof InputObjectTypeDefinition;
            if (valueType && !processedTypes.containsKey(typeDefinition.getName())
                    && !directiveTypes.contains(typeDefinition.getName())) {
                result.put(typeDefinition.getName(), typeDefinition);
            }
        }
        typeDefinitionRegistry.scalars().forEach((name, definition) -> {
            if (!ScalarInfo.isGraphqlSpecifiedScalar(name) && !manifestRecorder.isScalarReached(name)
                    && !directiveTypes.contains(name)) {
                result.put(name, definition);
            }
        });
        return result;
    }

    /**
     * Checks every {@link io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate} against the schema, its method
     * and its bean, before anything is bound.
     *
     * @param schemaDefinition the schema definition
     */
    void validateSchemaCoordinates(SchemaDefinition schemaDefinition) {
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
    void validateSchemaMappingDictionary() {
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
     * @param unboundTypes     the types left unbound on purpose
     * @param processedTypes   the bound types, by name
     * @param fieldBindings    what binds each field, by coordinate
     * @param resolverBindings the coordinates each resolver method binds, by method
     */
    void verifySchemaCoordinateBindings(Set<String> unboundTypes, Map<String, Class<?>> processedTypes,
                                        Map<String, String> fieldBindings,
                                        Map<String, Set<String>> resolverBindings) {
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
                        + describeBinding(coordinateMethod, typeName, processedTypes, fieldBindings) + ".");
            }
        }

        if (!problems.isEmpty()) {
            throw new InvalidSchemaCoordinateException(problems);
        }

        // in strict mode such a method is an error, see checkStrictMode()
        if (!unboundMethods.isEmpty() && !configuration.isStrict()) {
            LOG.warn("Methods whose @GraphQLSchemaCoordinate names an unreachable type are not bound: {}",
                    String.join("; ", unboundMethods));
        }
    }

    private String describeBinding(SchemaCoordinateMethod coordinateMethod, String typeName,
                                   Map<String, Class<?>> processedTypes, Map<String, String> fieldBindings) {
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

}
