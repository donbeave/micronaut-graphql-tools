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

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLScalarType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.exceptions.RuntimeWiringOverrideException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Applies the {@link RuntimeWiringCustomizer} beans to the library's runtime wiring and fails if one of them replaced
 * what the library wired, directly in the builder or through a code registry.
 */
@Internal
final class RuntimeWiringCustomizers {

    private RuntimeWiringCustomizers() {
    }

    /**
     * Applies the customizers in the given order.
     *
     * @param builder     the builder that holds the library's wiring
     * @param customizers the customizers, in {@code Ordered} order
     * @param registry    the registry the schema is generated from, for the types and fields of the schema
     * @throws RuntimeWiringOverrideException if a customizer replaced what the library wired
     */
    static void apply(RuntimeWiring.Builder builder,
                      List<RuntimeWiringCustomizer> customizers,
                      TypeDefinitionRegistry registry) {
        if (customizers.isEmpty()) {
            return;
        }
        Map<String, Set<String>> fieldsByType = objectTypeFields(registry);
        List<String> replacements = new ArrayList<>();
        Snapshot before = Snapshot.of(builder.build(), fieldsByType.keySet());
        for (RuntimeWiringCustomizer customizer : customizers) {
            customizer.customize(builder);
            Snapshot after = Snapshot.of(builder.build(), fieldsByType.keySet());
            String by = " (" + customizer.getClass().getName() + ")";
            before.replacedIn(after, fieldsByType).forEach(it -> replacements.add(it + by));
            before = after;
        }
        if (!replacements.isEmpty()) {
            throw new RuntimeWiringOverrideException(replacements);
        }
    }

    /**
     * @param registry the registry
     * @return the name of every object type (extensions included) to the names of its fields
     */
    private static Map<String, Set<String>> objectTypeFields(TypeDefinitionRegistry registry) {
        Map<String, Set<String>> fieldsByType = new TreeMap<>();
        List<ObjectTypeDefinition> objectTypes = new ArrayList<>();
        for (TypeDefinition<?> type : registry.types().values()) {
            if (type instanceof ObjectTypeDefinition objectType) {
                objectTypes.add(objectType);
            }
        }
        registry.objectTypeExtensions().values().forEach(objectTypes::addAll);
        for (ObjectTypeDefinition objectType : objectTypes) {
            Set<String> fields = fieldsByType.computeIfAbsent(objectType.getName(), it -> new TreeSet<>());
            for (FieldDefinition field : objectType.getFieldDefinitions()) {
                fields.add(field.getName());
            }
        }
        registry.types().values().forEach(type -> fieldsByType.putIfAbsent(type.getName(), new TreeSet<>()));
        return fieldsByType;
    }

    /**
     * What a runtime wiring holds at one moment. The maps of a {@link RuntimeWiring} are those of its builder, so they
     * are copied.
     *
     * @param dataFetchers        type name to field name to data fetcher
     * @param defaultDataFetchers type name to default data fetcher
     * @param wiringFactory       the wiring factory
     * @param scalars             scalar name to scalar
     * @param typeResolvers       type name to type resolver
     * @param directiveWirings    directive name to the wiring registered for it
     * @param codeRegistry        the code registry the schema generator starts from
     */
    private record Snapshot(
            Map<String, Map<String, DataFetcher>> dataFetchers,
            Map<String, DataFetcher> defaultDataFetchers,
            @Nullable WiringFactory wiringFactory,
            Map<String, GraphQLScalarType> scalars,
            Map<String, TypeResolver> typeResolvers,
            Map<String, SchemaDirectiveWiring> directiveWirings,
            GraphQLCodeRegistry codeRegistry
    ) {

        static Snapshot of(RuntimeWiring runtimeWiring, Set<String> typeNames) {
            Map<String, Map<String, DataFetcher>> dataFetchers = new TreeMap<>();
            runtimeWiring.getDataFetchers().forEach((type, fields) -> dataFetchers.put(type, new TreeMap<>(fields)));
            Map<String, DataFetcher> defaultDataFetchers = new TreeMap<>();
            for (String typeName : typeNames) {
                DataFetcher dataFetcher = runtimeWiring.getDefaultDataFetcherForType(typeName);
                if (dataFetcher != null) {
                    defaultDataFetchers.put(typeName, dataFetcher);
                }
            }
            return new Snapshot(
                    dataFetchers,
                    defaultDataFetchers,
                    runtimeWiring.getWiringFactory(),
                    new TreeMap<>(runtimeWiring.getScalars()),
                    new TreeMap<>(runtimeWiring.getTypeResolvers()),
                    new TreeMap<>(runtimeWiring.getRegisteredDirectiveWiring()),
                    runtimeWiring.getCodeRegistry()
            );
        }

        /**
         * @param after        the wiring after a customizer ran
         * @param fieldsByType the fields of every object type
         * @return what this snapshot wires that {@code after} wires differently
         */
        List<String> replacedIn(Snapshot after, Map<String, Set<String>> fieldsByType) {
            List<String> replaced = new ArrayList<>();
            dataFetchers.forEach((type, fields) -> fields.forEach((field, dataFetcher) -> {
                if (after.dataFetchers.getOrDefault(type, Map.of()).get(field) != dataFetcher) {
                    replaced.add("data fetcher of " + type + "." + field);
                }
            }));
            defaultDataFetchers.forEach((type, dataFetcher) -> {
                if (after.defaultDataFetchers.get(type) != dataFetcher) {
                    replaced.add("default data fetcher of " + type);
                }
            });
            if (after.wiringFactory != wiringFactory) {
                replaced.add("wiring factory");
            }
            scalars.forEach((name, scalar) -> {
                if (after.scalars.get(name) != scalar) {
                    replaced.add("scalar " + name);
                }
            });
            typeResolvers.forEach((type, typeResolver) -> {
                if (after.typeResolvers.get(type) != typeResolver) {
                    replaced.add("type resolver of " + type);
                }
            });
            directiveWirings.forEach((name, wiring) -> {
                if (after.directiveWirings.get(name) != wiring) {
                    replaced.add("directive wiring @" + name);
                }
            });
            replaced.addAll(replacedThroughCodeRegistry(after, fieldsByType));
            return replaced;
        }

        /**
         * graphql-java's schema generator keeps a data fetcher or type resolver that the code registry of the runtime
         * wiring already holds, so one there for a field or type the library wired would win over the library.
         */
        private List<String> replacedThroughCodeRegistry(Snapshot after, Map<String, Set<String>> fieldsByType) {
            List<String> replaced = new ArrayList<>();
            GraphQLCodeRegistry.Builder now = GraphQLCodeRegistry.newCodeRegistry(after.codeRegistry);
            GraphQLCodeRegistry.Builder then = GraphQLCodeRegistry.newCodeRegistry(codeRegistry);
            Set<String> wiredFields = new TreeSet<>();
            dataFetchers.forEach((type, fields) -> fields.keySet().forEach(field -> wiredFields.add(type + "." + field)));
            defaultDataFetchers.keySet().forEach(type -> fieldsByType.getOrDefault(type, Set.of())
                    .forEach(field -> wiredFields.add(type + "." + field)));
            for (String coordinate : wiredFields) {
                int dot = coordinate.indexOf('.');
                FieldCoordinates coordinates = FieldCoordinates.coordinates(
                        coordinate.substring(0, dot), coordinate.substring(dot + 1));
                if (now.hasDataFetcher(coordinates) && !then.hasDataFetcher(coordinates)) {
                    replaced.add("data fetcher of " + coordinate + ", through the code registry");
                }
            }
            for (String type : typeResolvers.keySet()) {
                if (now.hasTypeResolver(type) && !then.hasTypeResolver(type)) {
                    replaced.add("type resolver of " + type + ", through the code registry");
                }
            }
            return replaced;
        }

    }

}
