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
package io.micronaut.graphql.tools.binding.internal.rules;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.Locale;

/**
 * The one rule for a field that a member of the Java type (a property or a {@code @GraphQLField} method) and a type
 * resolver method can both bind, for object and interface fields alike. The startup and the compile-time checks decide
 * with it.
 *
 * <p>The field-level intent is stronger than the setting: a field that carries a resolver directive, or whose type
 * resolver method declares its coordinate with {@code @GraphQLSchemaCoordinate}, is bound by the method. A member
 * marked {@code @GraphQLIgnore} is no member, so nothing is ambiguous.</p>
 */
@Internal
public final class PrecedenceRules {

    private PrecedenceRules() {
    }

    /**
     * The {@code precedence} setting, independent of {@code strict}.
     */
    public enum Setting {

        /** An ambiguity without field-level intent fails. */
        FAIL("fail"),
        /** The type resolver method wins; the ambiguity is logged. */
        RESOLVER_FIRST("resolver-first"),
        /** The member wins; the ambiguity is logged. */
        PROPERTY_FIRST("property-first");

        private final String token;

        Setting(String token) {
            this.token = token;
        }

        /**
         * @return the value in the configuration
         */
        public String token() {
            return token;
        }

        /**
         * @param token a configuration value, or {@code null}
         * @return the setting, or {@code null} when the value is none of them
         */
        @Nullable
        public static Setting fromToken(@Nullable String token) {
            if (token == null) {
                return null;
            }
            String value = token.strip().toLowerCase(Locale.ROOT);
            for (Setting setting : values()) {
                if (setting.token.equals(value)) {
                    return setting;
                }
            }
            return null;
        }
    }

    /**
     * What binds the field.
     */
    public enum Outcome {

        /** The binding fails with the precedence ambiguity. */
        FAIL,
        /** The type resolver method binds the field. */
        RESOLVER,
        /** The member of the Java type binds the field. */
        MEMBER
    }

    /**
     * @param setting the {@code precedence} setting
     * @param intent  whether the field says which side binds it: a resolver directive, or a type resolver method that
     *                declares its coordinate
     * @return what binds the field; a decision without intent is logged as an ambiguity unless it fails
     */
    public static Outcome decide(Setting setting, boolean intent) {
        if (intent) {
            return Outcome.RESOLVER;
        }
        return switch (setting) {
            case FAIL -> Outcome.FAIL;
            case RESOLVER_FIRST -> Outcome.RESOLVER;
            case PROPERTY_FIRST -> Outcome.MEMBER;
        };
    }

}
