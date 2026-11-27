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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.inject.InjectionPoint;

/**
 * Creates the {@link GraphQLHttpClient} of an injection point. The transport is the HTTP client that the registry returns for the
 * service of {@link GraphQLClient}, so discovery, load balancing, filters and TLS apply as for any Micronaut HTTP client.
 */
@Internal
@Factory
final class GraphQLHttpClientFactory {

    static final String DEFAULT_ENDPOINT = "/graphql";

    @Prototype
    GraphQLHttpClient graphQLHttpClient(@Nullable InjectionPoint<?> injectionPoint, HttpClientRegistry<?> registry, GraphQLCodec codec) {
        AnnotationMetadata metadata = injectionPoint == null ? AnnotationMetadata.EMPTY_METADATA : injectionPoint.getAnnotationMetadata();
        if (!metadata.hasAnnotation(GraphQLClient.class)) {
            throw new ConfigurationException("A GraphQLHttpClient is injected with @GraphQLClient(\"<service id>\"), for example "
                    + "@Inject @GraphQLClient(\"books\") GraphQLHttpClient client");
        }
        return new DefaultGraphQLHttpClient(registry.getClient(metadata), codec, endpoint(metadata), path(metadata));
    }

    /**
     * Returns the path to request on the transport of a client: empty when {@code @GraphQLClient(endpoint)} is set, because the
     * transport was created with that path, and {@code /graphql} otherwise.
     *
     * @param metadata the annotation metadata of the client
     * @return the path
     */
    static String endpoint(AnnotationMetadata metadata) {
        boolean endpointGiven = metadata.stringValue(GraphQLClient.class, "endpoint").filter(path -> !path.isEmpty()).isPresent();
        return endpointGiven ? "" : DEFAULT_ENDPOINT;
    }

    /**
     * Returns the path of the endpoint on the server: the {@code endpoint} member of {@code @GraphQLClient}, and {@code /graphql} when it is empty.
     *
     * @param metadata the annotation metadata of the client
     * @return the path
     */
    static String path(AnnotationMetadata metadata) {
        return metadata.stringValue(GraphQLClient.class, "endpoint").filter(path -> !path.isEmpty()).orElse(DEFAULT_ENDPOINT);
    }
}
