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
package io.micronaut.graphql.tools.processor;

import io.micronaut.context.annotation.Infrastructure;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

/**
 * Records the methods that carry {@link GraphQLSchemaCoordinate}, on any bean, so that the coordinates can be
 * validated at startup.
 */
@Internal
@Singleton
@Infrastructure
public final class GraphQLSchemaCoordinateMethodProcessor implements ExecutableMethodProcessor<GraphQLSchemaCoordinate> {

    private final GraphQLResolversRegistry graphQLResolversRegistry;

    public GraphQLSchemaCoordinateMethodProcessor(GraphQLResolversRegistry graphQLResolversRegistry) {
        this.graphQLResolversRegistry = graphQLResolversRegistry;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        graphQLResolversRegistry.registerSchemaCoordinateExecutableMethod(
                beanDefinition, (ExecutableMethod<Object, ?>) method
        );
    }

}
