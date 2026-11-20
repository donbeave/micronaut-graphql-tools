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
package io.micronaut.graphql.tools.scalars.internal;

import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.scalars.StandardScalar;
import jakarta.inject.Singleton;

import java.util.regex.Pattern;

/**
 * Reads the configuration of the scalars: {@code micronaut.graphql.tools.scalars.<id>.name} and {@code ....format}. A value that
 * the scalar cannot use fails the startup with a message that names the key.
 */
@Internal
@Singleton
public final class ScalarSettings {

    /** The prefix of every key of this module. */
    public static final String PREFIX = "micronaut.graphql.tools.scalars.";

    private static final Pattern NAME = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*");

    private final Environment environment;

    ScalarSettings(Environment environment) {
        this.environment = environment;
    }

    /**
     * @param scalar a scalar
     * @return its name in the schema: the configured name, or the default
     * @throws ConfigurationException when the configured name is not a valid GraphQL name
     */
    public String name(StandardScalar scalar) {
        String key = PREFIX + scalar.id() + ".name";
        String name = value(key);
        if (name == null) {
            return scalar.defaultName();
        }
        if (!NAME.matcher(name).matches() || name.startsWith("__")) {
            throw new ConfigurationException("The value '" + name + "' of " + key + " is not a valid GraphQL name: use letters, "
                    + "digits and underscores, not starting with a digit or with two underscores.");
        }
        return name;
    }

    /**
     * @param scalar a scalar
     * @return the configured wire format, or {@code null} when none is configured
     * @throws ConfigurationException when the scalar has no configurable format
     */
    @Nullable
    public String format(StandardScalar scalar) {
        String key = PREFIX + scalar.id() + ".format";
        String format = value(key);
        if (format != null && !scalar.isFormatConfigurable()) {
            throw new ConfigurationException(key + " is set, but the scalar " + scalar.id() + " has one wire format and cannot be configured.");
        }
        return format;
    }

    @Nullable
    private String value(String key) {
        String value = environment.getProperty(key, String.class).orElse(null);
        if (value != null && value.isBlank()) {
            throw new ConfigurationException(key + " is empty. Remove the key to use the default.");
        }
        return value;
    }

}
