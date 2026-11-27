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
package io.micronaut.graphql.tools.binding.internal.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;

import java.util.List;

/**
 * What a request resolves to: the operation, or the findings that say why there is none. A request that fails for a cause
 * that an earlier request of the same schema already reported has neither: the finding is reported once.
 *
 * @param operation the assembled operation, null when the request failed
 * @param findings  the findings of the request, empty when it passed or when its cause was reported before
 */
@Internal
public record ClientOutcome(@Nullable ClientOperation operation, List<Finding> findings) {

    public ClientOutcome {
        findings = List.copyOf(findings);
    }

    static ClientOutcome of(ClientOperation operation) {
        return new ClientOutcome(operation, List.of());
    }

    static ClientOutcome failed(List<Finding> findings) {
        return new ClientOutcome(null, findings);
    }
}
