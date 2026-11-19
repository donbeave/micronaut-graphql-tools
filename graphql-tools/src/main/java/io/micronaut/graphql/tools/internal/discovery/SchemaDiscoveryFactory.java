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
package io.micronaut.graphql.tools.internal.discovery;

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration;

/**
 * Reads the schema files of {@code micronaut.graphql.tools.schema.locations} into the {@link TypeDefinitionRegistry} that the
 * schema is bound from. The bean is {@code @Secondary}: any {@code TypeDefinitionRegistry} bean of the application is used
 * instead, and then no file is read: the bean is not even a candidate when another bean enumerates the registries.
 */
@Internal
@Factory
public final class SchemaDiscoveryFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaDiscoveryFactory.class);

    @Bean
    @Singleton
    @Secondary
    @Requires(missingBeans = TypeDefinitionRegistry.class)
    public TypeDefinitionRegistry discoveredTypeDefinitionRegistry(BeanContext beanContext,
                                                                   GraphQLToolsConfiguration configuration) {
        SchemaSources.Loaded loaded = SchemaDiscovery.discover(configuration.getSchema().getLocations(),
                beanContext.getClassLoader());
        LOG.debug("Read the GraphQL schema from {}", loaded.read());
        if (!loaded.skipped().isEmpty()) {
            LOG.info("Skipped {}: only executable definitions or nothing, so no schema", loaded.skipped());
        }
        return loaded.registry();
    }

}
