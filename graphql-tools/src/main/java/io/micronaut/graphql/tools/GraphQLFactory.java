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
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

/**
 * @author Alexey Zhokhov
 */
@Internal
@Factory
public final class GraphQLFactory {

    @Bean
    @Singleton
    public GraphQL graphQL(ApplicationContext applicationContext,
                           GraphQLResolversRegistry graphQLResolversRegistry,
                           TypeDefinitionRegistry typeDefinitionRegistry,
                           SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer) {
        SchemaMappingDictionary schemaMappingDictionary = new SchemaMappingDictionary();
        schemaMappingDictionaryCustomizer.customize(schemaMappingDictionary);

        GraphQLSchemaProvider graphQLSchemaProvider = new GraphQLSchemaProvider();

        GraphQLRuntimeWiringGenerator graphQLRuntimeWiringGenerator = new GraphQLRuntimeWiringGenerator(
                applicationContext,
                new GraphQLBeanIntrospectionRegistry(),
                graphQLResolversRegistry,
                typeDefinitionRegistry,
                schemaMappingDictionary,
                graphQLSchemaProvider
        );

        RuntimeWiring runtimeWiring = graphQLRuntimeWiringGenerator.generate();

        // destroys GraphQLResolversRegistry as it no use in runtime after we initialized RuntimeWiring successfully
        applicationContext.destroyBean(graphQLResolversRegistry);

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        graphQLSchemaProvider.init(graphQLSchema);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

}
