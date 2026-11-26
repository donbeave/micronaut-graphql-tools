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
package io.micronaut.graphql.tools.internal;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;
import io.micronaut.graphql.tools.binding.internal.schema.TypeExtensions;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.customizer.GraphQLRuntimeWiringCustomizer;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer;
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer;
import io.micronaut.graphql.tools.directive.SchemaDirective;
import io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator;
import io.micronaut.graphql.tools.internal.checks.DirectiveWiringCheck;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestHolder;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.internal.wiring.GraphQLRuntimeWiringGenerator;
import io.micronaut.graphql.tools.internal.wiring.SchemaDirectiveWirings;
import io.micronaut.graphql.tools.internal.wiring.WiringComponents;
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Binds the application's {@link TypeDefinitionRegistry} to its resolvers and builds the executable
 * {@link GraphQLSchema}, so that the {@link GraphQLFactory} method stays a one-line bean method. The customizers run in
 * this order: the type definition registry and the schema mapping dictionary customizers, the binding, the runtime
 * wiring customizers, and the schema customizers.
 */
@Internal
@Singleton
final class SchemaBinder {

    private final ApplicationContext applicationContext;
    private final GraphQLResolversRegistry graphQLResolversRegistry;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLToolsConfiguration configuration;
    private final BindingManifestHolder bindingManifestHolder;
    private final List<GraphQLSchemaMappingDictionaryCustomizer> schemaMappingDictionaryCustomizers;
    private final List<GraphQLTypeDefinitionRegistryCustomizer> typeDefinitionRegistryCustomizers;
    private final List<GraphQLRuntimeWiringCustomizer> runtimeWiringCustomizers;
    private final List<GraphQLSchemaCustomizer> graphQLSchemaCustomizers;
    private final List<BeanRegistration<SchemaDirectiveWiring>> directiveWirings;
    private final List<BeanRegistration<SchemaDirective>> schemaDirectives;
    private final List<GraphQLFieldFetcherDecorator> fieldFetcherDecorators;

    // the application context gives the environment of the check configuration; everything else is a bean
    SchemaBinder(ApplicationContext applicationContext,
                 GraphQLResolversRegistry graphQLResolversRegistry,
                 TypeDefinitionRegistry typeDefinitionRegistry,
                 GraphQLToolsConfiguration configuration,
                 BindingManifestHolder bindingManifestHolder,
                 List<GraphQLSchemaMappingDictionaryCustomizer> schemaMappingDictionaryCustomizers,
                 List<GraphQLTypeDefinitionRegistryCustomizer> typeDefinitionRegistryCustomizers,
                 List<GraphQLRuntimeWiringCustomizer> runtimeWiringCustomizers,
                 List<GraphQLSchemaCustomizer> graphQLSchemaCustomizers,
                 List<BeanRegistration<SchemaDirectiveWiring>> directiveWirings,
                 List<BeanRegistration<SchemaDirective>> schemaDirectives,
                 List<GraphQLFieldFetcherDecorator> fieldFetcherDecorators) {
        this.applicationContext = applicationContext;
        this.graphQLResolversRegistry = graphQLResolversRegistry;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.configuration = configuration;
        this.bindingManifestHolder = bindingManifestHolder;
        this.schemaMappingDictionaryCustomizers = schemaMappingDictionaryCustomizers;
        this.typeDefinitionRegistryCustomizers = typeDefinitionRegistryCustomizers;
        this.runtimeWiringCustomizers = runtimeWiringCustomizers;
        this.graphQLSchemaCustomizers = graphQLSchemaCustomizers;
        this.directiveWirings = directiveWirings;
        this.schemaDirectives = schemaDirectives;
        this.fieldFetcherDecorators = fieldFetcherDecorators;
    }

    GraphQLSchema bind() {
        SchemaMappingDictionary schemaMappingDictionary = new SchemaMappingDictionary();
        schemaMappingDictionaryCustomizers.forEach(customizer -> customizer.customize(schemaMappingDictionary));

        TypeDefinitionRegistry registry = new TypeDefinitionRegistry().merge(typeDefinitionRegistry);
        typeDefinitionRegistryCustomizers.forEach(customizer -> customizer.customize(registry));
        SchemaSources.addImplicitRoots(registry);

        TypeDefinitionRegistry merged = TypeExtensions.merge(registry);
        Set<String> extensionFields = TypeExtensions.objectExtensionFields(registry);
        WiringComponents components = WiringComponents.of(applicationContext, configuration, fieldFetcherDecorators);
        GraphQLRuntimeWiringGenerator generator = new GraphQLRuntimeWiringGenerator(
                applicationContext,
                components,
                graphQLResolversRegistry,
                merged,
                schemaMappingDictionary,
                configuration,
                extensionFields
        );

        List<GraphQLRuntimeWiringCustomizer> customizers = new ArrayList<>();
        SchemaDirectiveWirings schemaDirectiveWirings = new SchemaDirectiveWirings(directiveWirings, schemaDirectives);
        customizers.add(schemaDirectiveWirings);
        customizers.addAll(runtimeWiringCustomizers);

        RuntimeWiring runtimeWiring = generator.generate(customizers);
        schemaDirectiveWirings.checkUnwrapped(runtimeWiring);

        DirectiveWiringCheck.check(registry, runtimeWiring, schemaDirectiveWirings.getEveryElementDirectiveNames(),
                schemaDirectiveWirings.getDirectiveNames(), configuration, components.checkPolicy(),
                components.manifestRecorder());
        components.checkPolicy().verifyAllowedFindings();

        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);

        for (GraphQLSchemaCustomizer customizer : graphQLSchemaCustomizers) {
            graphQLSchema = Objects.requireNonNull(customizer.customize(graphQLSchema),
                    () -> "GraphQLSchemaCustomizer " + customizer.getClass().getName()
                            + " returned null instead of a schema");
        }

        bindingManifestHolder.set(components.manifestRecorder().build(registry));
        return graphQLSchema;
    }
}
