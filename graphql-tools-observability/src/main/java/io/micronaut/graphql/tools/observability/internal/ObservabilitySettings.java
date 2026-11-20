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
package io.micronaut.graphql.tools.observability.internal;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.observability.GraphQLObservability;

import java.util.List;

/**
 * The configuration under {@code micronaut.graphql.tools.observability}.
 */
@Internal
@ConfigurationProperties(GraphQLObservability.PREFIX)
public final class ObservabilitySettings {

    private boolean enabled = true;
    private Metrics metrics = new Metrics();
    private Tracing tracing = new Tracing();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }

    /** The keys under {@code metrics}. */
    @ConfigurationProperties("metrics")
    public static final class Metrics {

        /** The default of {@link #getMaxOperations()}. */
        public static final int DEFAULT_MAX_OPERATIONS = 100;

        private boolean enabled = true;
        private int maxOperations = DEFAULT_MAX_OPERATIONS;

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

    /** The keys under {@code tracing}. */
    @ConfigurationProperties("tracing")
    public static final class Tracing {

        private boolean enabled = true;
        private List<String> fields = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

}
