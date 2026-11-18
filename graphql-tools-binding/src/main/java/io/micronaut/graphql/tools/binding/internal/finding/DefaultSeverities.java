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
package io.micronaut.graphql.tools.binding.internal.finding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The one table of default severities: every check code is {@link Severity#FAIL}. {@code strict=false} is
 * {@link Severity#WARN} for every code. An explicit {@code checks} entry for the code wins over both.
 */
@Internal
public final class DefaultSeverities {

    /** The default severity of each check code. */
    public static final Map<BindingCode, Severity> TABLE;

    static {
        Map<BindingCode, Severity> table = new EnumMap<>(BindingCode.class);
        for (BindingCode code : BindingCode.values()) {
            if (code.check()) {
                table.put(code, Severity.FAIL);
            }
        }
        TABLE = Collections.unmodifiableMap(table);
    }

    private DefaultSeverities() {
    }

    /**
     * @param code   a check code
     * @param strict the value of the {@code strict} shorthand
     * @return the severity of the check when no {@code checks} entry is set
     */
    public static Severity of(BindingCode code, boolean strict) {
        Severity severity = TABLE.get(code);
        if (severity == null) {
            throw new IllegalArgumentException(code + " is not a check");
        }
        return strict ? severity : Severity.WARN;
    }
}
