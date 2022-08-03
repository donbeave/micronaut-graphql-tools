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
package io.micronaut.graphql.tools.schema;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class UnionTypeResolver implements TypeResolver {

    private final Provider<GraphQLSchema> graphQLSchemaProvider;

    // source class -> target GraphQL type name
    private final Map<Class<?>, String> objectTypes;

    public UnionTypeResolver(Provider<GraphQLSchema> graphQLSchemaProvider,
                             Map<Class<?>, String> objectTypes) {
        this.graphQLSchemaProvider = graphQLSchemaProvider;
        this.objectTypes = new ConcurrentHashMap<>(objectTypes);
    }

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        String graphQlType = objectTypes.get(env.getObject().getClass());

        return graphQLSchemaProvider.get().getObjectType(graphQlType);
    }

}
