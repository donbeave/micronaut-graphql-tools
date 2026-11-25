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
package io.micronaut.graphql.tools.internal.manifest;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The read-only management endpoint {@code graphql-binding}: it serves the {@link BindingManifest} as JSON, the same lines that the
 * startup file output writes. The bean exists only when Micronaut Management is on the classpath and the manifest is recorded
 * ({@code micronaut.graphql.tools.manifest.enabled}); it is off until {@code endpoints.graphql-binding.enabled} is {@code true}, and the standard {@code endpoints.graphql-binding.sensitive}
 * key sets its sensitivity. The first read renders the manifest, so the operation runs on the blocking executor, never on a GraphQL request.
 */
@Internal
@Endpoint(id = BindingManifestEndpoint.ID, defaultEnabled = false)
@Requires(classes = Endpoint.class)
@Requires(property = GraphQLToolsConfiguration.PREFIX + ".manifest.enabled", value = "true")
public final class BindingManifestEndpoint {

    static final String ID = "graphql-binding";

    private final BeanProvider<BindingManifest> bindingManifest;

    BindingManifestEndpoint(BeanProvider<BindingManifest> bindingManifest) {
        this.bindingManifest = bindingManifest;
    }

    @Read
    @ExecuteOn(TaskExecutors.BLOCKING)
    public Map<String, Object> read() {
        BindingManifest manifest = bindingManifest.get();
        Map<String, Object> body = new LinkedHashMap<>(4);
        body.put("header", manifest.getHeader());
        body.put("lines", manifest.getLines());
        return body;
    }

}
