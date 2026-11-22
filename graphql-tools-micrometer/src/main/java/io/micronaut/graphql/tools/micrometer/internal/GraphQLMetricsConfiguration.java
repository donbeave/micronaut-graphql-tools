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
package io.micronaut.graphql.tools.micrometer.internal;

import io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.Toggleable;

/**
 * The configuration under {@code micronaut.metrics.binders.graphql}, where Micronaut keeps the keys of its metrics binders.
 */
@Internal
@ConfigurationProperties(GraphQLMetricsConfiguration.PREFIX)
public final class GraphQLMetricsConfiguration implements Toggleable {

    /** The prefix of the keys. */
    public static final String PREFIX = MeterRegistryFactory.MICRONAUT_METRICS_BINDERS + ".graphql";

    /** The default of {@link #getMaxOperations()}. */
    public static final int DEFAULT_MAX_OPERATIONS = 100;

    private boolean enabled = true;
    private int maxOperations = DEFAULT_MAX_OPERATIONS;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxOperations() {
        return maxOperations;
    }

    public void setMaxOperations(int maxOperations) {
        this.maxOperations = maxOperations;
    }

}
