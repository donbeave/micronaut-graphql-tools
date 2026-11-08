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
package io.micronaut.graphql.tools.schema;

import graphql.TypeResolutionEnvironment;
import graphql.execution.UnresolvedTypeException;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Provider;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The type resolver of every GraphQL union and interface. It resolves the object type of a runtime object in two
 * steps:
 *
 * <ol>
 *     <li>by its exact class: the class an object type is mapped to, or for a Java interface the classes annotated with
 *     {@code @GraphQLType(theInterface)};</li>
 *     <li>otherwise by assignability: the one object type whose Java type the object is an instance of (a subclass, a
 *     runtime subclass such as a proxy, or an implementation of a mapped Java interface).</li>
 * </ol>
 *
 * <p>An object that matches no object type, or more than one in the second step, is an
 * {@link UnresolvedTypeException}, which graphql-java reports as an error of that field, with its path; the message
 * names the abstract type, the class and the candidates. The result is cached per class.</p>
 *
 * @author Alexey Zhokhov
 */
@Internal
public final class AbstractTypeResolver implements TypeResolver {

    private final Provider<GraphQLSchema> graphQLSchemaProvider;
    private final String abstractTypeKind;
    private final String abstractTypeName;

    // exact class -> object type name
    private final Map<Class<?>, String> exactClasses;

    // object type name -> the Java type it is mapped to, sorted by name
    private final Map<String, Class<?>> objectTypes;

    // runtime class -> object type name, or the error message
    private final Map<Class<?>, Resolution> resolutions = new ConcurrentHashMap<>();

    /**
     * @param graphQLSchemaProvider the schema, once it is built
     * @param abstractTypeKind      {@code union} or {@code interface}, for the error message
     * @param abstractTypeName      the name of the union or interface
     * @param exactClasses          the classes that resolve to an object type by their exact class
     * @param objectTypes           the Java type each object type of the union or interface is mapped to
     */
    public AbstractTypeResolver(Provider<GraphQLSchema> graphQLSchemaProvider, String abstractTypeKind,
                                String abstractTypeName, Map<Class<?>, String> exactClasses,
                                Map<String, Class<?>> objectTypes) {
        this.graphQLSchemaProvider = graphQLSchemaProvider;
        this.abstractTypeKind = abstractTypeKind;
        this.abstractTypeName = abstractTypeName;
        this.exactClasses = Map.copyOf(exactClasses);
        this.objectTypes = new TreeMap<>(objectTypes);
    }

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        Class<?> objectClass = env.getObject().getClass();
        GraphQLSchema schema = graphQLSchemaProvider.get();

        Resolution resolution = resolutions.computeIfAbsent(objectClass, this::resolve);

        if (resolution.objectType() == null) {
            throw new UnresolvedTypeException(
                    resolution.error(),
                    (GraphQLNamedOutputType) schema.getType(abstractTypeName)
            );
        }

        return schema.getObjectType(resolution.objectType());
    }

    private Resolution resolve(Class<?> objectClass) {
        String exact = exactClasses.get(objectClass);
        if (exact != null) {
            return new Resolution(exact, null);
        }

        List<String> candidates = objectTypes.entrySet().stream()
                .filter(it -> it.getValue().isAssignableFrom(objectClass))
                .map(Map.Entry::getKey)
                .toList();

        if (candidates.size() == 1) {
            return new Resolution(candidates.get(0), null);
        }

        if (candidates.isEmpty()) {
            return new Resolution(null, "The object of class " + objectClass.getName() + " is none of the object "
                    + "types of " + abstractTypeKind + " " + abstractTypeName + ": " + describe(objectTypes.keySet())
                    + ".");
        }

        return new Resolution(null, "The object of class " + objectClass.getName() + " is an instance of more than "
                + "one object type of " + abstractTypeKind + " " + abstractTypeName + ": " + describe(candidates)
                + ". Map its class to one of them, or let it implement only one of their Java types.");
    }

    private String describe(Collection<String> names) {
        String result = names.stream()
                .map(it -> it + " (" + objectTypes.get(it).getName() + ")")
                .collect(Collectors.joining(", "));
        return result.isEmpty() ? "none is mapped" : result;
    }

    private record Resolution(String objectType, String error) {
    }

}
