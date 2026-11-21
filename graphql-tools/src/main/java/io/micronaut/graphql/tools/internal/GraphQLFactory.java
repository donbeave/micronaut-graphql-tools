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

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import io.micronaut.graphql.tools.customizer.GraphQLBuilderCustomizer;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.customizer.RuntimeWiringCustomizer;
import io.micronaut.graphql.tools.execution.FieldFetcherDecorator;
import io.micronaut.graphql.tools.directive.SchemaDirective;
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;
import io.micronaut.graphql.tools.binding.internal.schema.TypeExtensions;
import io.micronaut.graphql.tools.internal.checks.DirectiveWiringCheck;
import io.micronaut.graphql.tools.internal.protection.DocumentCache;
import io.micronaut.graphql.tools.internal.error.LibraryDataFetcherExceptionHandler;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestHolder;
import io.micronaut.graphql.tools.internal.model.GraphQLBeanIntrospectionRegistry;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.internal.wiring.GraphQLRuntimeWiringGenerator;
import io.micronaut.graphql.tools.internal.wiring.SchemaDirectiveWirings;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates the {@link GraphQLSchema} bound from the application's {@link TypeDefinitionRegistry} and the
 * {@link GraphQL} bean that runs it. Each of them steps aside when the application provides an unqualified or
 * {@code @Primary} bean of that type: an application that builds its own {@code GraphQL} (instrumentation, execution
 * strategies, transport) consumes the library's {@code GraphQLSchema} bean, and an application that provides its own
 * {@code GraphQLSchema} gets no binding from the library at all. A bean with a qualifier (for example
 * {@code @Named("admin")}) does not make them step aside; the library's beans are {@code @Primary}, so an unqualified
 * injection point still gets them.
 *
 * <p>The {@code GraphQL} bean installs the application's {@code Instrumentation} beans: none when there is no bean, the bean
 * itself when there is one, and a {@code ChainedInstrumentation} in bean order when there are several. The
 * {@code GraphQLBuilderCustomizer} beans run after that.</p>
 *
 * <p>The bean also applies the {@code protection} settings: the parsed-document cache, the depth and complexity limits (two
 * more instrumentations) and the introspection switch.</p>
 *
 * @author Alexey Zhokhov
 */
@Internal
@Factory
public final class GraphQLFactory {

    @Bean
    @Singleton
    @Primary
    @Requires(condition = LibraryBeanSelection.NoCompetingSchema.class)
    public GraphQLSchema graphQLSchema(ApplicationContext applicationContext,
                                       GraphQLResolversRegistry graphQLResolversRegistry,
                                       TypeDefinitionRegistry typeDefinitionRegistry,
                                       List<SchemaMappingDictionaryCustomizer> schemaMappingDictionaryCustomizers,
                                       GraphQLToolsConfiguration configuration,
                                       List<TypeDefinitionRegistryCustomizer> typeDefinitionRegistryCustomizers,
                                       List<RuntimeWiringCustomizer> runtimeWiringCustomizers,
                                       List<GraphQLSchemaCustomizer> graphQLSchemaCustomizers,
                                       List<BeanRegistration<SchemaDirectiveWiring>> directiveWirings,
                                       List<BeanRegistration<SchemaDirective>> schemaDirectives,
                                       BindingManifestHolder bindingManifestHolder,
                                       List<FieldFetcherDecorator> fieldFetcherDecorators) {
        SchemaMappingDictionary schemaMappingDictionary = new SchemaMappingDictionary();
        schemaMappingDictionaryCustomizers.forEach(customizer -> customizer.customize(schemaMappingDictionary));

        TypeDefinitionRegistry registry = new TypeDefinitionRegistry().merge(typeDefinitionRegistry);
        typeDefinitionRegistryCustomizers.forEach(customizer -> customizer.customize(registry));
        SchemaSources.addImplicitRoots(registry);

        GraphQLRuntimeWiringGenerator graphQLRuntimeWiringGenerator = new GraphQLRuntimeWiringGenerator(
                applicationContext,
                new GraphQLBeanIntrospectionRegistry(),
                graphQLResolversRegistry,
                TypeExtensions.merge(registry),
                schemaMappingDictionary,
                configuration,
                TypeExtensions.objectExtensionFields(registry),
                fieldFetcherDecorators
        );

        List<RuntimeWiringCustomizer> customizers = new ArrayList<>();
        SchemaDirectiveWirings schemaDirectiveWirings = new SchemaDirectiveWirings(
                directiveWirings,
                schemaDirectives
        );
        customizers.add(schemaDirectiveWirings);
        customizers.addAll(runtimeWiringCustomizers);

        RuntimeWiring runtimeWiring = graphQLRuntimeWiringGenerator.generate(customizers);
        schemaDirectiveWirings.checkUnwrapped(runtimeWiring);

        DirectiveWiringCheck.check(registry, runtimeWiring, schemaDirectiveWirings.getEveryElementDirectiveNames(),
                schemaDirectiveWirings.getDirectiveNames(), configuration,
                graphQLRuntimeWiringGenerator.getCheckPolicy(), graphQLRuntimeWiringGenerator.getManifestRecorder());
        graphQLRuntimeWiringGenerator.getCheckPolicy().verifyAllowedFindings();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(registry, runtimeWiring);

        for (GraphQLSchemaCustomizer customizer : graphQLSchemaCustomizers) {
            graphQLSchema = Objects.requireNonNull(customizer.customize(graphQLSchema),
                    () -> "GraphQLSchemaCustomizer " + customizer.getClass().getName()
                            + " returned null instead of a schema");
        }

        bindingManifestHolder.set(graphQLRuntimeWiringGenerator.getManifestRecorder().build(registry));

        return graphQLSchema;
    }

    @Bean
    @Singleton
    @Primary
    @Requires(condition = LibraryBeanSelection.NoCompetingGraphQL.class)
    public GraphQL graphQL(GraphQLSchema graphQLSchema, List<GraphQLBuilderCustomizer> graphQLBuilderCustomizers,
                           LibraryDataFetcherExceptionHandler exceptionHandler, List<Instrumentation> instrumentations,
                           GraphQLToolsConfiguration configuration) {
        GraphQLToolsConfiguration.ProtectionConfiguration protection = configuration.getProtection();
        if (!protection.isIntrospection()) {
            GraphQLSchema bound = graphQLSchema;
            graphQLSchema = bound.transform(schema -> schema.codeRegistry(bound.getCodeRegistry()
                    .transform(registry -> registry.fieldVisibility(NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY))));
        }
        GraphQL.Builder builder = GraphQL.newGraphQL(graphQLSchema)
                .defaultDataFetcherExceptionHandler(exceptionHandler);
        if (protection.getDocumentCacheSize() > 0) {
            builder.preparsedDocumentProvider(new DocumentCache(protection.getDocumentCacheSize()));
        }
        List<Instrumentation> chain = new ArrayList<>(instrumentations);
        if (protection.getMaxDepth() > 0) {
            chain.add(new MaxQueryDepthInstrumentation(protection.getMaxDepth()));
        }
        if (protection.getMaxComplexity() > 0) {
            chain.add(new MaxQueryComplexityInstrumentation(protection.getMaxComplexity()));
        }
        if (chain.size() == 1) {
            builder.instrumentation(chain.get(0));
        } else if (!chain.isEmpty()) {
            builder.instrumentation(new ChainedInstrumentation(chain));
        }
        graphQLBuilderCustomizers.forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    /**
     * @param graphQLSchema         the library's schema, so that the schema is bound first
     * @param bindingManifestHolder the manifest recorded while the schema was bound
     * @return what the schema is bound to
     */
    @Bean
    @Singleton
    public BindingManifest bindingManifest(GraphQLSchema graphQLSchema, BindingManifestHolder bindingManifestHolder) {
        return bindingManifestHolder.get();
    }

}
