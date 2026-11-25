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
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

import java.util.List;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.internal.execution.ExecutionContextAvailability;
import io.micronaut.graphql.tools.internal.execution.LibraryGraphQLInvocation;

/**
 * Binds the schema and sets up the library's invocation at startup, so a binding failure or an unknown executor stops the
 * application before the first request.
 */
@Internal
@Singleton
@Requires(property = GraphQLToolsConfiguration.EAGER_INIT, notEquals = StringUtils.FALSE)
public final class EagerBinding implements ApplicationEventListener<StartupEvent> {

    private final BeanProvider<GraphQLSchema> schema;
    private final BeanProvider<LibraryGraphQLInvocation> invocation;

    private final BeanContext beanContext;

    EagerBinding(BeanProvider<GraphQLSchema> schema, BeanProvider<LibraryGraphQLInvocation> invocation, BeanContext beanContext) {
        this.schema = schema;
        this.invocation = invocation;
        this.beanContext = beanContext;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        rejectAmbiguousInvocation();
        schema.get();
        // an executor key that names no executor fails the startup too
        if (ExecutionContextAvailability.isAvailable(beanContext)) {
            invocation.ifPresent(bean -> { });
        }
    }

    private void rejectAmbiguousInvocation() {
        if (beanContext.findBeanDefinition(LibraryGraphQLInvocation.class).isEmpty()) {
            return;
        }
        List<String> templates = LibraryBeanSelection.eachInvocationTemplates(beanContext);
        if (!templates.isEmpty()) {
            throw new ConfigurationException("The application declares " + String.join(", ", templates)
                    + " with @EachBean(GraphQLInvocation.class), which the library's own invocation matches too, and that"
                    + " invocation needs the GraphQL engine that such a bean provides. Fix: target the application's own bean"
                    + " type instead of GraphQLInvocation, or provide an independent unqualified or @Primary GraphQL bean (for a GraphQLSchema template, an unqualified or @Primary GraphQLSchema also works).");
        }
        List<String> secondary = LibraryBeanSelection.secondaryInvocations(beanContext);
        if (!secondary.isEmpty()) {
            throw new ConfigurationException("The application's only GraphQLInvocation beans are @Secondary: "
                    + String.join(", ", secondary) + ". The library's own invocation is @Secondary too, so an unqualified"
                    + " GraphQLInvocation injection point finds two equal candidates. Fix: remove @Secondary from the"
                    + " application's invocation, or make it @Primary.");
        }
    }

}
