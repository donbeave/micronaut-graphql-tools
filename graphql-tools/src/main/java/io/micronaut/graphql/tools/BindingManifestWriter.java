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
package io.micronaut.graphql.tools;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Writes the {@link BindingManifest} at startup when {@code micronaut.graphql.tools.manifest.enabled} is
 * {@code true}: to {@code micronaut.graphql.tools.manifest.path}, or to the log. This binds the schema at startup.
 */
@Internal
@Singleton
@Requires(property = GraphQLToolsConfiguration.PREFIX + ".manifest.enabled", value = "true")
public final class BindingManifestWriter implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(BindingManifestWriter.class);

    private final BeanProvider<BindingManifest> bindingManifest;
    private final GraphQLToolsConfiguration.ManifestConfiguration configuration;

    BindingManifestWriter(BeanProvider<BindingManifest> bindingManifest,
                          GraphQLToolsConfiguration.ManifestConfiguration configuration) {
        this.bindingManifest = bindingManifest;
        this.configuration = configuration;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        BindingManifest manifest = bindingManifest.get();
        if (configuration.getPath() == null) {
            LOG.info("GraphQL binding manifest:\n{}", manifest.render());
            return;
        }
        Path path = Path.of(configuration.getPath());
        try {
            manifest.write(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the GraphQL binding manifest to " + path.toAbsolutePath(), e);
        }
        LOG.info("GraphQL binding manifest written to {} ({} lines)", path.toAbsolutePath(),
                manifest.getLines().size());
    }

}
