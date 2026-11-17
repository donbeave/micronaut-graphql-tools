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
package io.micronaut.graphql.tools;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates the {@link GraphQLSchema} bound from the application's {@link TypeDefinitionRegistry} and the
 * {@link GraphQL} bean that runs it. Each of them steps aside when the application provides a bean of that type: an
 * application that builds its own {@code GraphQL} (instrumentation, execution strategies, transport) consumes the
 * library's {@code GraphQLSchema} bean, and an application that provides its own {@code GraphQLSchema} gets no
 * binding from the library at all.
 *
 * @author Alexey Zhokhov
 */
@Internal
@Factory
public final class GraphQLFactory {

    @Bean
    @Singleton
    @Requires(missingBeans = GraphQLSchema.class)
    public GraphQLSchema graphQLSchema(ApplicationContext applicationContext,
                                       GraphQLResolversRegistry graphQLResolversRegistry,
                                       TypeDefinitionRegistry typeDefinitionRegistry,
                                       SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer,
                                       GraphQLToolsConfiguration configuration,
                                       List<TypeDefinitionRegistryCustomizer> typeDefinitionRegistryCustomizers,
                                       List<RuntimeWiringCustomizer> runtimeWiringCustomizers,
                                       List<GraphQLSchemaCustomizer> graphQLSchemaCustomizers,
                                       BindingManifestHolder bindingManifestHolder) {
        SchemaMappingDictionary schemaMappingDictionary = new SchemaMappingDictionary();
        schemaMappingDictionaryCustomizer.customize(schemaMappingDictionary);

        // a copy: the customizers must not change the application's bean
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry().merge(typeDefinitionRegistry);
        typeDefinitionRegistryCustomizers.forEach(customizer -> customizer.customize(registry));

        GraphQLSchemaProvider graphQLSchemaProvider = new GraphQLSchemaProvider();

        GraphQLRuntimeWiringGenerator graphQLRuntimeWiringGenerator = new GraphQLRuntimeWiringGenerator(
                applicationContext,
                new GraphQLBeanIntrospectionRegistry(),
                graphQLResolversRegistry,
                // bound with the fields of their extensions; the schema is generated from the registry as parsed
                TypeExtensions.merge(registry),
                schemaMappingDictionary,
                graphQLSchemaProvider,
                configuration
        );

        // the directive beans first: an application's customizer then cannot replace them unnoticed
        List<RuntimeWiringCustomizer> customizers = new ArrayList<>();
        SchemaDirectiveWirings schemaDirectiveWirings = new SchemaDirectiveWirings(
                applicationContext.getBeanRegistrations(SchemaDirectiveWiring.class),
                applicationContext.getBeanRegistrations(SchemaDirective.class)
        );
        customizers.add(schemaDirectiveWirings);
        customizers.addAll(runtimeWiringCustomizers);

        RuntimeWiring runtimeWiring = graphQLRuntimeWiringGenerator.generate(customizers);

        DirectiveWiringCheck.check(registry, runtimeWiring, schemaDirectiveWirings.getEveryElementDirectiveNames(),
                schemaDirectiveWirings.getDirectiveNames(), configuration,
                graphQLRuntimeWiringGenerator.getManifestRecorder());

        // destroys GraphQLResolversRegistry as it no use in runtime after we initialized RuntimeWiring successfully
        applicationContext.destroyBean(graphQLResolversRegistry);

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(registry, runtimeWiring);

        for (GraphQLSchemaCustomizer customizer : graphQLSchemaCustomizers) {
            graphQLSchema = Objects.requireNonNull(customizer.customize(graphQLSchema),
                    () -> "GraphQLSchemaCustomizer " + customizer.getClass().getName()
                            + " returned null instead of a schema");
        }

        graphQLSchemaProvider.init(graphQLSchema);

        // from the customized registry and the final schema: the manifest describes what is served
        bindingManifestHolder.set(graphQLRuntimeWiringGenerator.getManifestRecorder().build(registry, graphQLSchema));

        return graphQLSchema;
    }

    @Bean
    @Singleton
    @Requires(missingBeans = GraphQL.class)
    public GraphQL graphQL(GraphQLSchema graphQLSchema, List<GraphQLBuilderCustomizer> graphQLBuilderCustomizers) {
        GraphQL.Builder builder = GraphQL.newGraphQL(graphQLSchema);
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
