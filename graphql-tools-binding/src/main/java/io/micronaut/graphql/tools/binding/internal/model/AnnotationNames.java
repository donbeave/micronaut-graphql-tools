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
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;

/**
 * The binary names of the library annotations that the rules read. The compile-time adapter cannot load the annotation
 * classes, so every rule refers to them by these names.
 */
@Internal
public final class AnnotationNames {

    private static final String PACKAGE = "io.micronaut.graphql.tools.annotation.";

    /** {@code @GraphQLIgnore}. */
    public static final String IGNORE = PACKAGE + "GraphQLIgnore";
    /** {@code @GraphQLRootResolver}. */
    public static final String ROOT_RESOLVER = PACKAGE + "GraphQLRootResolver";
    /** {@code @GraphQLTypeResolver}. */
    public static final String TYPE_RESOLVER = PACKAGE + "GraphQLTypeResolver";
    /** {@code @GraphQLSchemaCoordinate}. */
    public static final String SCHEMA_COORDINATE = PACKAGE + "GraphQLSchemaCoordinate";
    /** {@code @GraphQLArgument}. */
    public static final String ARGUMENT = PACKAGE + "GraphQLArgument";
    /** {@code @GraphQLInput}. */
    public static final String INPUT = PACKAGE + "GraphQLInput";
    /** {@code @GraphQLType}. */
    public static final String TYPE = PACKAGE + "GraphQLType";
    /** {@code @GraphQLField}. */
    public static final String FIELD = PACKAGE + "GraphQLField";

    /** {@code QueryResolver}, the marker interface of a root resolver of the query type. */
    public static final String QUERY_RESOLVER = "io.micronaut.graphql.tools.resolver.QueryResolver";
    /** {@code MutationResolver}, the marker interface of a root resolver of the mutation type. */
    public static final String MUTATION_RESOLVER = "io.micronaut.graphql.tools.resolver.MutationResolver";
    /** {@code SubscriptionResolver}, the marker interface of a root resolver of the subscription type. */
    public static final String SUBSCRIPTION_RESOLVER = "io.micronaut.graphql.tools.resolver.SubscriptionResolver";
    /** {@code TypeResolver}, the marker interface of a type resolver whose source type is its type argument. */
    public static final String TYPE_RESOLVER_MARKER = "io.micronaut.graphql.tools.resolver.TypeResolver";

    private AnnotationNames() {
    }
}
