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

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.BeanContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Logs at startup, at INFO, which {@code GraphQLSchema} and {@code GraphQL} beans the application runs, and why the
 * library's bean of that type is or is not one of them.
 */
@Internal
@Singleton
final class BeanSelectionLogger implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(BeanSelectionLogger.class);

    private final BeanContext beanContext;

    BeanSelectionLogger(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        if (LOG.isInfoEnabled()) {
            log(GraphQLSchema.class);
            log(GraphQL.class);
        }
    }

    private void log(Class<?> type) {
        String name = type.getSimpleName();
        LibraryBeanSelection.Provided competitor = LibraryBeanSelection.competitor(beanContext, type);
        if (competitor != null) {
            LOG.info("The library does not create a {} bean: the application provides {}, which is used for every unqualified {} injection point",
                    name, LibraryBeanSelection.describe(competitor), name);
        } else {
            LOG.info("The library creates the {} bean, which is used for every unqualified {} injection point{}",
                    name, name, ignoredSuffix(type));
        }
    }

    private String ignoredSuffix(Class<?> type) {
        List<String> others = new ArrayList<>();
        for (LibraryBeanSelection.Provided bean : LibraryBeanSelection.applicationBeans(beanContext, type)) {
            others.add(LibraryBeanSelection.describe(bean));
        }
        return others.isEmpty() ? "" : "; the application's qualified beans do not replace it: " + String.join(", ", others);
    }

}
