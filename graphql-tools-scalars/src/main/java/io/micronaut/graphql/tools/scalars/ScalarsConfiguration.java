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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.Toggleable;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The configuration of the standard scalars, under {@value #PREFIX}. {@code enabled} switches the whole module; each scalar is
 * configured under its id, see {@link ScalarConfiguration}. A key under the prefix that is not the id of a {@link StandardScalar},
 * and a key under the id of a scalar that is not one of its properties, fails the startup.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(ScalarsConfiguration.PREFIX)
public class ScalarsConfiguration implements Toggleable {

    /** The prefix of the keys of this module. */
    public static final String PREFIX = "micronaut.graphql.tools.scalars";

    /** The key that switches the whole module. */
    public static final String ENABLED_CONFIG = PREFIX + ".enabled";

    /** The default of {@link #isEnabled()}. */
    public static final boolean DEFAULT_ENABLED = true;

    private static final String ENABLED_KEY = "enabled";
    private static final List<String> SCALAR_KEYS = List.of(ENABLED_KEY, "name", "format");

    private boolean enabled = DEFAULT_ENABLED;
    private Map<String, ScalarConfiguration> scalars = Map.of();

    /**
     * @return whether the module registers its scalars; when {@code false} it registers none
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the module registers its scalars. Default: {@code true}.
     *
     * @param enabled {@code false} to register no scalar
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the configuration of the scalars that have a key, by {@link StandardScalar#id()}
     */
    @NonNull
    public Map<String, ScalarConfiguration> getScalars() {
        return scalars;
    }

    /**
     * Receives the scalars that the application configures. The entry named {@code enabled} is the switch of the module and is
     * skipped; any other key that is not the id of a {@link StandardScalar} fails the startup with a message that lists the valid ids.
     * A key under the id of a scalar that is not {@code enabled}, {@code name} or {@code format}, a misspelling such as
     * {@code local-date.nmae}, fails the startup with a message that names the whole key and lists those three.
     *
     * @param configured  the configuration of each scalar that has a key
     * @param environment the environment that holds the keys
     */
    @Inject
    void setScalars(Collection<ScalarConfiguration> configured, Environment environment) {
        Map<String, ScalarConfiguration> byId = new LinkedHashMap<>();
        for (ScalarConfiguration scalar : configured) {
            if (scalar.isKnown()) {
                rejectUnknownKeys(scalar, environment);
                byId.put(scalar.getId(), scalar);
            } else if (scalar.isConfigured() || !ENABLED_KEY.equals(scalar.getId())) {
                throw new ConfigurationException("The key " + PREFIX + "." + scalar.getId() + " is not a standard scalar. The valid ids are: "
                        + Arrays.stream(StandardScalar.values()).map(StandardScalar::id).collect(Collectors.joining(", ")) + ".");
            }
        }
        this.scalars = Map.copyOf(byId);
    }

    private static void rejectUnknownKeys(ScalarConfiguration scalar, Environment environment) {
        String prefix = PREFIX + "." + scalar.getId();
        for (String key : environment.getPropertyEntries(prefix)) {
            if (!SCALAR_KEYS.contains(key)) {
                throw new ConfigurationException("The key " + prefix + "." + key + " is not a property of the scalar " + scalar.getId()
                        + ". The valid properties are: " + String.join(", ", SCALAR_KEYS) + ".");
            }
        }
    }

    /**
     * @param scalar a scalar
     * @return its configuration, or {@code null} when the application configures nothing for it
     */
    @Nullable
    public ScalarConfiguration get(@NonNull StandardScalar scalar) {
        return scalars.get(scalar.id());
    }

}
