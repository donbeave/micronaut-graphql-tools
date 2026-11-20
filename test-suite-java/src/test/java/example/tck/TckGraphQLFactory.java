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
package example.tck;

import example.graphql.GraphQLFactory;
import example.graphql.type.SecurityError;
import example.graphql.type.ValidationError;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
import io.micronaut.graphql.tools.tck.Scenarios;
import io.micronaut.graphql.tools.tck.Tck;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * While a TCK scenario runs, the application serves the example schema and the extensions of that scenario's schema.
 */
@Factory
@Requires(property = Tck.SCENARIO_PROPERTY)
public class TckGraphQLFactory {

    @Bean
    @Singleton
    @Replaces(value = TypeDefinitionRegistry.class, factory = GraphQLFactory.class)
    public TypeDefinitionRegistry typeDefinitionRegistry(@Property(name = Tck.SCENARIO_PROPERTY) String scenario,
                                                         ResourceResolver resourceResolver) throws IOException {
        SchemaParser parser = new SchemaParser();
        try (InputStream example = resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()) {
            return new TypeDefinitionRegistry()
                    .merge(parser.parse(new String(example.readAllBytes(), StandardCharsets.UTF_8)))
                    .merge(parser.parse(Scenarios.schema(scenario)));
        }
    }

    // The example registers the classes of its union by name; a scenario with classes of that kind adds its own mappings.
    @Bean
    @Singleton
    @Replaces(value = SchemaMappingDictionaryCustomizer.class, factory = GraphQLFactory.class)
    public SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer(List<TckTypeMappings> scenarioMappings) {
        return dictionary -> {
            dictionary
                    .registerType("SecurityError", SecurityError.class)
                    .registerType("ValidationError", ValidationError.class);
            scenarioMappings.forEach(mappings -> mappings.register(dictionary));
        };
    }

}
