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
package io.micronaut.graphql.tools.ignore;

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;

@Requires(property = "spec.name", value = "IgnoreSpec")
@Singleton
@GraphQLRootResolver
public class JavaIgnoreQuery {

    public JavaIgnoreItem item() {
        return new JavaIgnoreItem();
    }

    public JavaIgnoreLevel level() {
        return JavaIgnoreLevel.HIGH;
    }

    public String describe(JavaIgnoreFilter filter) {
        return filter.getText();
    }

    @EventListener
    public void onStartup(StartupEvent event) {
    }

    @Scheduled(initialDelay = "1h", fixedDelay = "1h")
    public void tick() {
    }

    @PreDestroy
    public void close() {
    }

    @GraphQLIgnore
    public String helper(String value) {
        return value;
    }

}
