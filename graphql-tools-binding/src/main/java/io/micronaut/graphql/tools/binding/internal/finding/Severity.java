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
import io.micronaut.core.annotation.Nullable;

import java.util.Locale;

/**
 * What a check does with a finding: {@link #FAIL} fails the startup, {@link #WARN} logs it and goes on, {@link #IGNORE}
 * skips the check.
 */
@Internal
public enum Severity {

    FAIL,
    WARN,
    IGNORE;

    /**
     * @return the setting value, for example {@code warn}
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param token a setting value
     * @return the severity, or {@code null} if the token is not one
     */
    @Nullable
    public static Severity fromToken(@Nullable String token) {
        if (token == null) {
            return null;
        }
        for (Severity severity : values()) {
            if (severity.token().equals(token.trim().toLowerCase(Locale.ROOT))) {
                return severity;
            }
        }
        return null;
    }

    /**
     * @return the tokens, for an error message
     */
    public static String tokens() {
        return "fail, warn, ignore";
    }
}
