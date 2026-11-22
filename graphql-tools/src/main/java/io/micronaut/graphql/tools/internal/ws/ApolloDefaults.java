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
package io.micronaut.graphql.tools.internal.ws;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;

import java.util.Map;

/**
 * Sets {@code graphql.graphql-apollo-ws.enabled} to {@code false} where the application does not set it. The controller of the
 * deprecated Apollo protocol in {@code micronaut-graphql} reads a missing value as disabled, but the package condition of its
 * other beans reads it as enabled, so the {@code @Scheduled} keep-alive sender exists by default. It needs a
 * {@code WebSocketBroadcaster} that only a server provides, so a context without one fails to create it at startup (seen as a
 * {@code NoSuchBeanException} in a native image). The default has the lowest precedence: any value of the application wins.
 *
 * <p><b>Audience.</b> Internal. Not part of the API; do not import it. Key types: ApolloDefaults.</p>
 */
@Internal
@ContextConfigurer
public final class ApolloDefaults implements ApplicationContextConfigurer {

    static final String ENABLED = "graphql.graphql-apollo-ws.enabled";

    @Override
    public void configure(ApplicationContextBuilder builder) {
        builder.propertySources(PropertySource.of("graphql-tools-defaults", Map.of(ENABLED, "false"), Integer.MIN_VALUE));
    }
}
