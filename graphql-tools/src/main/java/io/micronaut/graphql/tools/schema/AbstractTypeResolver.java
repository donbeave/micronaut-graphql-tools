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
import io.micronaut.core.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The type resolver of every GraphQL union and interface. It resolves the object type of a runtime object in two
 * steps:
 *
 * <ol>
 *     <li>by its exact class: the class an object type is mapped to, or for a Java interface the classes annotated with
 *     {@code @GraphQLType(theInterface)};</li>
 *     <li>otherwise by assignability: the object type whose Java type the object is an instance of (a subclass, a
 *     runtime subclass such as a proxy, or an implementation of a mapped Java interface). When the object is an
 *     instance of several, the most specific one wins: the one whose Java type is a subtype of the Java types of all
 *     the others, whatever the order of the mappings.</li>
 * </ol>
 *
 * <p>An object that matches no object type, or whose candidates have no single most specific one (their Java types
 * are unrelated, or two object types are mapped to the same Java type), is an
 * {@link UnresolvedTypeException}, which graphql-java reports as an error of that field, with its path; the message
 * names the abstract type, the class and the candidates. The result, with the name of the resolved object type, is
 * cached per class in a {@link ClassValue}: a lookup takes no lock, and the cache does not keep a class, or its class
 * loader, alive.</p>
 *
 * @author Alexey Zhokhov
 */
@Internal
public final class AbstractTypeResolver implements TypeResolver {

    private final String abstractTypeKind;
    private final String abstractTypeName;

    // exact class -> object type name
    private final Map<Class<?>, String> exactClasses;

    // object type name -> the Java type it is mapped to, sorted by name
    private final Map<String, Class<?>> objectTypes;

    // runtime class -> object type name, or the error message; held by the class itself, so never a strong reference
    // to it. The names are resolved against the schema of the request, so that a schema another component replaced
    // after the binding (a BeanCreatedEventListener<GraphQLSchema>) is the one that answers.
    private final ClassValue<Resolution> resolutions = new ClassValue<>() {
        @Override
        protected Resolution computeValue(Class<?> type) {
            return resolve(type);
        }
    };

    /**
     * @param abstractTypeKind {@code union} or {@code interface}, for the error message
     * @param abstractTypeName the name of the union or interface
     * @param exactClasses     the classes that resolve to an object type by their exact class
     * @param objectTypes      the Java type each object type of the union or interface is mapped to
     */
    public AbstractTypeResolver(String abstractTypeKind, String abstractTypeName,
                                Map<Class<?>, String> exactClasses, Map<String, Class<?>> objectTypes) {
        this.abstractTypeKind = abstractTypeKind;
        this.abstractTypeName = abstractTypeName;
        this.exactClasses = Map.copyOf(exactClasses);
        this.objectTypes = new TreeMap<>(objectTypes);
    }

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        // the schema of the execution, not the one the binding saw: a replaced schema has its own object types
        GraphQLSchema schema = env.getSchema();
        Resolution resolution = resolutions.get(env.getObject().getClass());

        if (resolution.objectTypeName() == null) {
            throw new UnresolvedTypeException(
                    resolution.error(),
                    (GraphQLNamedOutputType) schema.getType(abstractTypeName)
            );
        }

        // getType, not getObjectType: the latter builds a message supplier on every call
        if (schema.getType(resolution.objectTypeName()) instanceof GraphQLObjectType objectType) {
            return objectType;
        }
        return schema.getObjectType(resolution.objectTypeName());
    }

    private Resolution resolve(Class<?> objectClass) {
        String exact = exactClasses.get(objectClass);
        if (exact != null) {
            return new Resolution(exact, null);
        }

        List<String> candidates = candidates(objectClass);

        if (candidates.size() == 1) {
            return new Resolution(candidates.get(0), null);
        }

        List<String> mostSpecific = mostSpecific(candidates);
        if (mostSpecific.size() == 1) {
            return new Resolution(mostSpecific.get(0), null);
        }

        if (candidates.isEmpty()) {
            return new Resolution(null, "The object of class " + objectClass.getName() + " is none of the object "
                    + "types of " + abstractTypeKind + " " + abstractTypeName + ": " + describe(objectTypes.keySet())
                    + ".");
        }

        return new Resolution(null, "The object of class " + objectClass.getName() + " is an instance of more than "
                + "one object type of " + abstractTypeKind + " " + abstractTypeName + ", and none is more specific than the "
                + "others: " + describe(candidates)
                + ". Map its class to one of them, or let it implement only one of their Java types.");
    }

    private List<String> candidates(Class<?> objectClass) {
        return objectTypes.entrySet().stream()
                .filter(it -> it.getValue().isAssignableFrom(objectClass))
                .map(Map.Entry::getKey)
                .toList();
    }

    // the most specific candidate: its Java type is a subtype of every other candidate's Java type
    private List<String> mostSpecific(List<String> candidates) {
        return candidates.stream()
                .filter(it -> candidates.stream()
                        .allMatch(other -> objectTypes.get(other).isAssignableFrom(objectTypes.get(it))))
                .toList();
    }

    /**
     * Finds out, without the schema, whether {@link #getType} would fail for an object of a class for sure: the class
     * is not mapped by its exact class, and is an instance of several object types of which none is more specific.
     *
     * @param objectClass the class of a possible object
     * @return the description of the ambiguity, with the object types and their Java types, or {@code null} if the
     * class resolves to one object type or to none
     */
    @Nullable
    public String findAmbiguity(Class<?> objectClass) {
        if (exactClasses.containsKey(objectClass)) {
            return null;
        }
        List<String> candidates = candidates(objectClass);
        if (candidates.size() < 2 || mostSpecific(candidates).size() == 1) {
            return null;
        }
        return objectClass.getName() + " is an instance of " + describe(candidates) + " of " + abstractTypeKind + " "
                + abstractTypeName;
    }

    /**
     * @return how many object types the union or interface has a Java type for
     */
    public int getObjectTypeCount() {
        return objectTypes.size();
    }

    private String describe(Collection<String> names) {
        String result = names.stream()
                .map(it -> it + " (" + objectTypes.get(it).getName() + ")")
                .collect(Collectors.joining(", "));
        return result.isEmpty() ? "none is mapped" : result;
    }

    private record Resolution(String objectTypeName, String error) {
    }

}
