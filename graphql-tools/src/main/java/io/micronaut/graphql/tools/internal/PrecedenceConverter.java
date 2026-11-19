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
package io.micronaut.graphql.tools.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration.Precedence;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.exceptions.InvalidConfigurationException;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;

/**
 * Reads {@code micronaut.graphql.tools.precedence}. The value {@code strict}, the former default, was renamed
 * {@code fail}; it is rejected with a message that names the replacement.
 */
@Internal
@Singleton
public final class PrecedenceConverter implements TypeConverter<CharSequence, Precedence> {

    @Override
    public Optional<Precedence> convert(CharSequence object, Class<Precedence> targetType, ConversionContext context) {
        String value = object.toString().strip().toLowerCase(Locale.ROOT).replace('_', '-');
        for (Precedence precedence : Precedence.values()) {
            if (precedence.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(value)) {
                return Optional.of(precedence);
            }
        }
        if (value.equals("strict")) {
            throw new InvalidConfigurationException(BindingCode.REMOVED_CONFIGURATION_KEY,
                    "The configuration uses a value that was removed in 1.0.0-M3 (BC-29):",
                    List.of(GraphQLToolsConfiguration.PREFIX + ".precedence: strict was renamed fail (the default); "
                            + "use fail, resolver-first or property-first. strict does not change it."));
        }
        context.reject(object, new IllegalArgumentException(GraphQLToolsConfiguration.PREFIX + ".precedence must be "
                + "fail, resolver-first or property-first, not " + object + "."));
        return Optional.empty();
    }

}
