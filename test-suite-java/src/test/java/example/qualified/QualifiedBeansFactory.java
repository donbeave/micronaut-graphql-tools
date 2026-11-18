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
package example.qualified;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
@Requires(env = QualifiedBeansFactory.ENVIRONMENT)
public class QualifiedBeansFactory {

    public static final String ENVIRONMENT = "qualified-graphql";

    @Bean
    @Singleton
    @Named("admin")
    GraphQLSchema adminSchema() {
        return new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse("schema { query: AdminQuery } type AdminQuery { secret: String }"),
                RuntimeWiring.newRuntimeWiring()
                        .type("AdminQuery", type -> type.dataFetcher("secret", new StaticDataFetcher("admin")))
                        .build());
    }

    @Bean
    @Singleton
    @Named("admin")
    GraphQL adminGraphQL(@Named("admin") GraphQLSchema schema) {
        return GraphQL.newGraphQL(schema).build();
    }

}
