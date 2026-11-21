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

import graphql.language.TypeDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.rules.TypeClassDerivation;
import io.micronaut.graphql.tools.internal.exceptions.configuration.InvalidConfigurationException;
import io.micronaut.graphql.tools.internal.exceptions.implementation.AmbiguousTypeClassException;
import io.micronaut.graphql.tools.internal.model.GraphQLBeanIntrospectionRegistry;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;

import java.util.List;

/**
 * The Java class of an object type that a union or an interface needs: the entry of the dictionary, else the class that
 * {@link TypeClassDerivation} derives from {@code @GraphQLType(name)} or the naming convention. An explicit entry always
 * wins, and two derived classes fail the startup. Where nothing else names a class and the declared Java type is sealed,
 * the permitted subclass of that name is the class. It runs once for each type at startup, from the compile-time
 * introspection metadata; a request resolves the class of an object through the cache of the type resolver.
 */
@Internal
final class TypeClasses {

    private final SchemaMappingDictionary dictionary;
    private final GraphQLBeanIntrospectionRegistry registry;
    @Nullable
    private final String convention;

    TypeClasses(SchemaMappingDictionary dictionary, GraphQLBeanIntrospectionRegistry registry, @Nullable String convention) {
        this.dictionary = dictionary;
        this.registry = registry;
        this.convention = convention;
        String problem = TypeClassDerivation.validate(convention);
        if (problem != null) {
            throw new InvalidConfigurationException(BindingCode.INVALID_TYPE_CLASS_CONVENTION,
                    "micronaut.graphql.tools.type-class-convention is not valid:", List.of(problem));
        }
    }

    /**
     * @param type           an object or interface type of a union or interface
     * @param mappingContext where the failure is reported
     * @return its class, or {@code null} when neither the dictionary nor the derivation names one
     * @throws AmbiguousTypeClassException if no dictionary entry exists and several classes match
     */
    @Nullable
    Class<?> find(TypeDefinition<?> type, MappingContext mappingContext) {
        Class<?> mapped = dictionary.getTypes().get(type.getName());
        if (mapped != null) {
            return mapped;
        }
        List<String> matches = TypeClassDerivation.matching(type.getName(), convention, registry.getTypeClassCandidates());
        if (matches.size() > 1) {
            throw new AmbiguousTypeClassException(mappingContext, type.getName(), matches);
        }
        return matches.isEmpty() ? null : registry.getCandidateClass(matches.get(0));
    }

    /**
     * @param type           an object type of a union or interface
     * @param declaredClass  the Java type that the field declares for the union or interface
     * @param mappingContext where the failure is reported
     * @return its class: the dictionary entry, else the derived class, else the permitted subclass of a sealed
     * {@code declaredClass}, else {@code null}
     * @throws AmbiguousTypeClassException if no dictionary entry exists and several classes match
     */
    @Nullable
    Class<?> find(TypeDefinition<?> type, Class<?> declaredClass, MappingContext mappingContext) {
        Class<?> found = find(type, mappingContext);
        if (found != null || !declaredClass.isSealed()) {
            return found;
        }
        return findPermitted(type, declaredClass, mappingContext);
    }

    // Every subclass of a sealed type is one of its permitted subclasses, so the introspected classes that the sealed type
    // is assignable from are its permitted subclasses that can be a member; no reflective lookup of the permits clause
    @Nullable
    private Class<?> findPermitted(TypeDefinition<?> objectType, Class<?> sealedClass, MappingContext mappingContext) {
        List<TypeClassDerivation.Candidate> permitted = registry.getTypeClassCandidates().stream()
                .filter(it -> sealedClass.isAssignableFrom(registry.getCandidateClass(it.className())))
                .toList();
        List<String> matches = TypeClassDerivation.matchingPermitted(objectType.getName(), permitted);
        if (matches.size() > 1) {
            throw new AmbiguousTypeClassException(mappingContext, objectType.getName(), matches);
        }
        return matches.isEmpty() ? null : registry.getCandidateClass(matches.get(0));
    }
}
