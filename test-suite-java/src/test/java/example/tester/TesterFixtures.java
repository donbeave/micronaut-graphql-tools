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
package example.tester;

import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class TesterFixtures {

    public static final String TESTER = "tester";

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = TESTER)
    GraphQLTypeDefinitionRegistryCustomizer testerSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "extend type Query {\n"
                        + "  testerBook(id: ID!): TesterBook\n"
                        + "  testerPages(count: Int!): Int!\n"
                        + "  testerWhoAmI: String!\n"
                        + "  testerFailing: String\n"
                        + "}\n"
                        + "type TesterBook {\n"
                        + "  id: ID!\n"
                        + "  title: String!\n"
                        + "}\n"));
    }
    // end::schema[]

}
