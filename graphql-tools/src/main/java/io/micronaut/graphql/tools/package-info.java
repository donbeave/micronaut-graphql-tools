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
/**
 * The runtime library: the configuration, the customizer and mapping types an application implements, and, for now,
 * the implementation of the binding at startup.
 *
 * <p><b>Audience.</b> This package mixes two audiences while the move described in the guide page "API Map" is
 * pending. The public API is the types that are not annotated {@code @Internal}: {@link io.micronaut.graphql.tools.GraphQLToolsConfiguration},
 * the customizers ({@link io.micronaut.graphql.tools.GraphQLBuilderCustomizer},
 * {@link io.micronaut.graphql.tools.GraphQLSchemaCustomizer}, {@link io.micronaut.graphql.tools.RuntimeWiringCustomizer},
 * {@link io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer},
 * {@link io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer}), the mapping types
 * ({@link io.micronaut.graphql.tools.SchemaMappingDictionary}, {@link io.micronaut.graphql.tools.GraphQLScalarMapping},
 * {@link io.micronaut.graphql.tools.MappingContext}), the directive types ({@link io.micronaut.graphql.tools.SchemaDirective},
 * {@link io.micronaut.graphql.tools.CustomWiring}), {@link io.micronaut.graphql.tools.ArgumentValue} and
 * {@link io.micronaut.graphql.tools.BindingManifest}.</p>
 *
 * <p>Every type annotated {@code @Internal} is implementation. Do not import it: it can change or move in any release.</p>
 *
 * <p>Annotations are in {@code io.micronaut.graphql.tools.annotation}, the failures in
 * {@code io.micronaut.graphql.tools.exceptions}.</p>
 */
package io.micronaut.graphql.tools;
