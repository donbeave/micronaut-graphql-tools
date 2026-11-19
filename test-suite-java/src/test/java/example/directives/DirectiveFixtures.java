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
package example.directives;

import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.graphql.tools.directive.SchemaDirective;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@Factory
public class DirectiveFixtures {

    public static final String CONFLICT = "directive-conflict";
    public static final String UNWIRED = "directive-unwired";
    public static final String SCHEMA_UNLISTED = "directive-schema-unlisted";

    @Bean
    @Singleton
    @Requires(env = CONFLICT)
    SchemaDirective upperForEveryElement() {
        return SchemaDirective.everyElement(List.of("upper"), new SchemaDirectiveWiring() { });
    }

    @Bean
    @Singleton
    @Requires(env = UNWIRED)
    @Replaces(TypeDefinitionRegistry.class)
    TypeDefinitionRegistry unwiredDirectiveRegistry(ResourceResolver resourceResolver) {
        return registryWith(resourceResolver, "directive @legacy on OBJECT\nextend type Book @legacy");
    }

    @Bean
    @Singleton
    @Requires(env = SCHEMA_UNLISTED)
    @Replaces(TypeDefinitionRegistry.class)
    TypeDefinitionRegistry schemaDirectiveRegistry(ResourceResolver resourceResolver) {
        return registryWith(resourceResolver, "directive @other on SCHEMA\nextend schema @other");
    }

    private static TypeDefinitionRegistry registryWith(ResourceResolver resourceResolver, String extension) {
        SchemaParser parser = new SchemaParser();
        return new TypeDefinitionRegistry()
                .merge(parser.parse(new BufferedReader(new InputStreamReader(
                        resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()))))
                .merge(parser.parse(extension));
    }

}
