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
package io.micronaut.graphql.tools.internal.input;

import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.internal.exceptions.mapping.InvalidScalarMappingException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.ScalarMappingNotProvidedException;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog.isGraphQlBuiltInType;

/**
 * The custom scalar mappings of the application by scalar name, checked for duplicates and for the names of the built-in
 * scalars.
 */
@Internal
public final class ScalarMappings {

    private final Map<String, GraphQLScalarMapping> byName;
    private final BindingManifestRecorder manifestRecorder;
    private final BeanContext beanContext;

    public ScalarMappings(Collection<GraphQLScalarMapping> mappings, BindingManifestRecorder manifestRecorder,
                          BeanContext beanContext) {
        this.byName = collect(mappings);
        this.manifestRecorder = manifestRecorder;
        this.beanContext = beanContext;
    }

    public Collection<GraphQLScalarMapping> all() {
        return byName.values();
    }

    private static Map<String, GraphQLScalarMapping> collect(Collection<GraphQLScalarMapping> scalarMappings) {
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

    public GraphQLScalarMapping require(ScalarTypeDefinition scalarTypeDefinition, MappingContext mappingContext) {
        String scalarName = scalarTypeDefinition.getName();
        GraphQLScalarMapping scalarMapping = byName.get(scalarName);
        if (scalarMapping == null) {
            throw new ScalarMappingNotProvidedException(mappingContext, scalarName, bareScalarTypeExists(scalarName));
        }
        manifestRecorder.scalar(scalarName);
        return scalarMapping;
    }

    private boolean bareScalarTypeExists(String scalarName) {
        try {
            return beanContext.getBeansOfType(GraphQLScalarType.class).stream()
                    .anyMatch(it -> it.getName().equals(scalarName));
        } catch (RuntimeException e) {
            // The hint is best effort: a scalar bean that cannot be created must not hide the missing mapping.
            return false;
        }
    }

}
