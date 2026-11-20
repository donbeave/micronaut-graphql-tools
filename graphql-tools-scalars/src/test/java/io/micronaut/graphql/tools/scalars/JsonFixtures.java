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
package io.micronaut.graphql.tools.scalars;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/** Values that are not JSON yet, and that Micronaut Serialization knows how to write. */
public final class JsonFixtures {

    private JsonFixtures() {
    }

    @Serdeable
    public record Reading(String sensor, double value, List<String> tags, Map<String, Integer> limits, Reading previous) {
    }

}
