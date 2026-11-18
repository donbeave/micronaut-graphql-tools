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

import graphql.language.SchemaDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.GraphQLResolversRegistry.SchemaCoordinateMethod;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates.ResolverKind;
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException;
import io.micronaut.inject.BeanDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The checks of the explicit {@code @GraphQLSchemaCoordinate} declarations: before the binding, that each names a valid
 * field of the right resolver kind; after it, that each method is bound to exactly that field.
 */
@Internal
final class SchemaCoordinateValidator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    private final CheckPolicy checkPolicy;

    SchemaCoordinateValidator(TypeDefinitionRegistry typeDefinitionRegistry,
                              GraphQLResolversRegistry graphQLResolversRegistry,
                              GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry,
                              CheckPolicy checkPolicy) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.graphQLBeanIntrospectionRegistry = graphQLBeanIntrospectionRegistry;
        this.checkPolicy = checkPolicy;
    }

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

    @Nullable
    private String checkSchemaCoordinate(SchemaCoordinateMethod coordinateMethod, Set<String> operationTypeNames) {
        BeanDefinition<?> beanDefinition = coordinateMethod.beanDefinition();
        boolean rootResolver = beanDefinition.hasStereotype(GraphQLRootResolver.class);
        boolean typeResolver = beanDefinition.hasStereotype(GraphQLTypeResolver.class);

        if (!rootResolver && !typeResolver) {
            return "the bean is neither a @" + GraphQLRootResolver.class.getSimpleName() + " nor a @"
                    + GraphQLTypeResolver.class.getSimpleName() + ".";
        }

        Set<ResolverKind> kinds = EnumSet.noneOf(ResolverKind.class);
        if (rootResolver) {
            kinds.add(ResolverKind.ROOT);
        }
        if (typeResolver) {
            kinds.add(ResolverKind.TYPE);
        }
        return SchemaCoordinates.check(typeDefinitionRegistry, operationTypeNames, coordinateMethod.coordinate(),
                coordinateMethod.method().getMethodName(), kinds).orElse(null);
    }

    Optional<RuntimeException> verifySchemaCoordinateBindings(Set<String> unboundTypes,
                                                              Map<String, Class<?>> processedTypes,
                                                              Map<String, String> fieldBindings,
                                                              Map<String, Set<String>> resolverBindings) {
        List<String> problems = new ArrayList<>();
        List<String> unboundMethods = new ArrayList<>();

        for (SchemaCoordinateMethod coordinateMethod : graphQLResolversRegistry.getSchemaCoordinateMethods()) {
            String coordinate = coordinateMethod.coordinate();
            String typeName = SchemaCoordinates.typeName(coordinate);
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
            return Optional.of(new InvalidSchemaCoordinateException(problems));
        }

        if (!unboundMethods.isEmpty() && !checkPolicy.failing(BindingCode.UNBOUND_RESOLVER_METHOD)) {
            LOG.warn("Methods whose @GraphQLSchemaCoordinate names an unreachable type are not bound: {}",
                    String.join("; ", unboundMethods));
        }
        return Optional.empty();
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

}
