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
package io.micronaut.graphql.tools.internal.wiring;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator;
import io.micronaut.graphql.tools.internal.checks.CheckPolicy;
import io.micronaut.graphql.tools.internal.execution.FieldExecutors;
import io.micronaut.graphql.tools.internal.execution.GraphQLFieldFetcherDecorators;
import io.micronaut.graphql.tools.internal.input.ScalarMappings;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.GraphQLBeanIntrospectionRegistry;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;

import java.util.List;

/**
 * The collaborators of the runtime wiring that are built once for a schema, so that the generator receives them and does
 * not build them from the application context. They are built together, in the order that reports an invalid check
 * configuration before an invalid scalar mapping.
 *
 * @param introspections    the bean introspections of the mapped classes
 * @param checkPolicy       the severity of each check
 * @param manifestRecorder  the recorder of the binding manifest
 * @param scalarMappings    the custom scalar mappings
 * @param fieldExecutors    the executors of the fields that hop
 * @param fieldDecorators   the field fetcher decorators
 */
@Internal
public record WiringComponents(GraphQLBeanIntrospectionRegistry introspections, CheckPolicy checkPolicy,
                               BindingManifestRecorder manifestRecorder, ScalarMappings scalarMappings,
                               FieldExecutors fieldExecutors, GraphQLFieldFetcherDecorators fieldDecorators) {

    /**
     * @param context       the application context, which gives the environment, the scalar mappings and the executors
     * @param configuration the library configuration
     * @param decorators    the field fetcher decorator beans
     * @return the components of one schema
     */
    public static WiringComponents of(ApplicationContext context, GraphQLToolsConfiguration configuration,
                                      List<GraphQLFieldFetcherDecorator> decorators) {
        CheckPolicy checkPolicy = CheckPolicy.of(configuration, context);
        BindingManifestRecorder manifestRecorder = new BindingManifestRecorder(configuration.getManifest().isEnabled());
        return new WiringComponents(new GraphQLBeanIntrospectionRegistry(), checkPolicy, manifestRecorder,
                new ScalarMappings(context.getBeansOfType(GraphQLScalarMapping.class), manifestRecorder, context),
                new FieldExecutors(context, configuration), new GraphQLFieldFetcherDecorators(decorators));
    }
}
