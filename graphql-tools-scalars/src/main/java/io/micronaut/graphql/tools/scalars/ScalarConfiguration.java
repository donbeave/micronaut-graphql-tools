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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.Toggleable;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * The configuration of one standard scalar, under {@code micronaut.graphql.tools.scalars.<id>}, where the id is the
 * {@link StandardScalar#id()}.
 *
 * @since 1.0.0
 */
@EachProperty(ScalarsConfiguration.PREFIX)
public class ScalarConfiguration implements Toggleable {

    private static final Pattern NAME = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*");

    private final String id;
    private final StandardScalar scalar;
    private boolean enabled = ScalarsConfiguration.DEFAULT_ENABLED;
    private String name;
    private String format;
    private boolean configured;

    /**
     * @param id the key of the scalar in the configuration
     */
    public ScalarConfiguration(@Parameter String id) {
        this.id = id;
        this.scalar = Arrays.stream(StandardScalar.values()).filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * @return the id of the scalar, the key under which it is configured
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Whether this entry stands for the scalar of its id, so that a key that is not an id can be told from the module's own keys.
     *
     * @return {@code true} when the id is the id of a {@link StandardScalar}
     */
    boolean isKnown() {
        return scalar != null;
    }

    /**
     * @return whether a property of this entry was set
     */
    boolean isConfigured() {
        return configured;
    }

    /**
     * @return whether the scalar has a mapping
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the scalar has a mapping. Turn a scalar off to register your own mapping with the same name. Default:
     * {@code true}.
     *
     * @param enabled {@code false} to register no mapping for the scalar
     */
    public void setEnabled(boolean enabled) {
        this.configured = true;
        this.enabled = enabled;
    }

    /**
     * @return the name of the scalar in the schema: the configured name, or the default name of the scalar
     */
    @NonNull
    public String getName() {
        return name != null ? name : scalar != null ? scalar.defaultName() : id;
    }

    /**
     * Sets the name of the scalar in the schema. It must be a GraphQL name that does not start with two underscores. Default:
     * the name in the table of the scalars page.
     *
     * @param name the name
     * @throws ConfigurationException when the name is blank or not a valid GraphQL name
     */
    public void setName(@NonNull String name) {
        String key = key("name");
        if (name.isBlank()) {
            throw new ConfigurationException(key + " is empty. Remove the key to use the default.");
        }
        if (!NAME.matcher(name).matches() || name.startsWith("__")) {
            throw new ConfigurationException("The value '" + name + "' of " + key + " is not a valid GraphQL name: use letters, "
                    + "digits and underscores, not starting with a digit or with two underscores.");
        }
        this.name = name;
        this.configured = true;
    }

    /**
     * @return the configured wire format, or {@code null} for the default
     */
    @Nullable
    public String getFormat() {
        return format;
    }

    /**
     * Sets the wire format of a {@code java.time} scalar: {@code iso}, a keyword of the scalar or a
     * {@code DateTimeFormatter} pattern. Default: {@code iso}.
     *
     * @param format the format
     * @throws ConfigurationException when the format is blank or the scalar has one wire format only
     */
    public void setFormat(@NonNull String format) {
        String key = key("format");
        if (format.isBlank()) {
            throw new ConfigurationException(key + " is empty. Remove the key to use the default.");
        }
        if (scalar != null && !scalar.isFormatConfigurable()) {
            throw new ConfigurationException(key + " is set, but the scalar " + id + " has one wire format and cannot be configured.");
        }
        this.format = format;
        this.configured = true;
    }

    private String key(String property) {
        return ScalarsConfiguration.PREFIX + "." + id + "." + property;
    }

}
