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

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The type resolver of every GraphQL union and interface: resolves the object type of the runtime object by the exact
 * class the object type is mapped to. An object of any other class is an {@link UnresolvedTypeException}, which
 * graphql-java reports as an error of that field, with its path; the message names the abstract type and the class.
 *
 * @author Alexey Zhokhov
 */
@Internal
public final class AbstractTypeResolver implements TypeResolver {

    private final Provider<GraphQLSchema> graphQLSchemaProvider;
    private final String abstractTypeKind;
    private final String abstractTypeName;

    // source class -> target GraphQL type name
    private final Map<Class<?>, String> objectTypes;

    /**
     * @param graphQLSchemaProvider the schema, once it is built
     * @param abstractTypeKind {@code union} or {@code interface}, for the error message
     * @param abstractTypeName the name of the union or interface
     * @param objectTypes      the class each object type of the union or interface is mapped to
     */
    public AbstractTypeResolver(Provider<GraphQLSchema> graphQLSchemaProvider, String abstractTypeKind,
                                String abstractTypeName, Map<Class<?>, String> objectTypes) {
        this.graphQLSchemaProvider = graphQLSchemaProvider;
        this.abstractTypeKind = abstractTypeKind;
        this.abstractTypeName = abstractTypeName;
        this.objectTypes = Map.copyOf(objectTypes);
    }

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        Object object = env.getObject();
        GraphQLSchema schema = graphQLSchemaProvider.get();

        String graphQlType = objectTypes.get(object.getClass());

        if (graphQlType == null) {
            throw new UnresolvedTypeException(
                    "The object of class " + object.getClass().getName() + " is none of the object types of "
                            + abstractTypeKind + " " + abstractTypeName + ": " + describeObjectTypes() + ".",
                    (GraphQLNamedOutputType) schema.getType(abstractTypeName)
            );
        }

        return schema.getObjectType(graphQlType);
    }

    private String describeObjectTypes() {
        if (objectTypes.isEmpty()) {
            return "none is mapped";
        }
        return objectTypes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(it -> it.getValue() + " (" + it.getKey().getName() + ")")
                .collect(Collectors.joining(", "));
    }

}
