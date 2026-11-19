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
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;

/** Binds the schema at startup, so a binding failure stops the application before the first request. */
@Internal
@Singleton
@Requires(property = GraphQLToolsConfiguration.PREFIX + ".eager-init", notEquals = "false")
public final class EagerBinding implements ApplicationEventListener<StartupEvent> {

    private final BeanProvider<GraphQLSchema> schema;

    EagerBinding(BeanProvider<GraphQLSchema> schema) {
        this.schema = schema;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        schema.get();
    }

}
