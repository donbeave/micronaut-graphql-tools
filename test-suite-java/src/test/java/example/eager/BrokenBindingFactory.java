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
package example.eager;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Factory
@Requires(env = BrokenBindingFactory.ENVIRONMENT)
public class BrokenBindingFactory {

    public static final String ENVIRONMENT = "broken-binding";

    @Bean
    @Singleton
    @Replaces(TypeDefinitionRegistry.class)
    TypeDefinitionRegistry brokenTypeDefinitionRegistry(ResourceResolver resourceResolver) {
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        registry.merge(parser.parse(new BufferedReader(new InputStreamReader(
                resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()))));
        registry.merge(parser.parse("extend type Query { unbound: String }"));
        return registry;
    }

}
