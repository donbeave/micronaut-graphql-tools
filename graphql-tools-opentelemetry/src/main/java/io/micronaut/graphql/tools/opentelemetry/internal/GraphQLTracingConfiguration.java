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
package io.micronaut.graphql.tools.opentelemetry.internal;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.Toggleable;

import java.util.List;

/**
 * The configuration under {@code micronaut.graphql.tools.tracing}.
 */
@Internal
@ConfigurationProperties(GraphQLTracingConfiguration.PREFIX)
public final class GraphQLTracingConfiguration implements Toggleable {

    /** The prefix of the keys. */
    public static final String PREFIX = "micronaut.graphql.tools.tracing";

    private boolean enabled = true;
    private List<String> fields = List.of();
    private boolean addOperationNameToSpanName;

    @Override
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

    public boolean isAddOperationNameToSpanName() {
        return addOperationNameToSpanName;
    }

    public void setAddOperationNameToSpanName(boolean addOperationNameToSpanName) {
        this.addOperationNameToSpanName = addOperationNameToSpanName;
    }

}
