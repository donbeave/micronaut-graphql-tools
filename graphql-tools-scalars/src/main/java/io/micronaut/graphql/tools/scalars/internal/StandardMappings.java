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

import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.scalars.StandardScalar;
import io.micronaut.graphql.tools.scalars.internal.json.JsonCoercing;
import io.micronaut.graphql.tools.scalars.internal.time.TimeScalars;
import io.micronaut.json.JsonMapper;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds the {@link GraphQLScalarMapping} of a {@link StandardScalar} with its configured name and format.
 */
@Internal
final class StandardMappings {

    private static final String JSON_DESCRIPTION = "Any JSON value: an object, an array, a string, a number, a boolean or null.";

    private StandardMappings() {
    }

    /**
     * @param scalar the scalar
     * @param settings the configuration
     * @param mapper the source of the {@link JsonMapper}, for the JSON scalar only
     * @return the mapping
     */
    static GraphQLScalarMapping create(StandardScalar scalar, ScalarSettings settings, @Nullable Supplier<JsonMapper> mapper) {
        String name = settings.name(scalar);
        switch (scalar.group()) {
            case JAVA_TIME:
                return timeMapping(scalar, name, settings.format(scalar));
            case JSON:
                settings.format(scalar);
                GraphQLScalarType json = GraphQLScalarType.newScalar().name(name).description(JSON_DESCRIPTION)
                        .coercing(new JsonCoercing(name, mapper)).build();
                return GraphQLScalarMapping.builder(json)
                        .outputClasses(Object.class)
                        .inputClasses(Object.class, Map.class, List.class, String.class, Boolean.class, Integer.class, Long.class,
                                BigInteger.class, Double.class)
                        .build();
            default:
                settings.format(scalar);
                return extended(scalar, name);
        }
    }

    private static GraphQLScalarMapping timeMapping(StandardScalar scalar, String name, @Nullable String format) {
        try {
            GraphQLScalarType type = GraphQLScalarType.newScalar().name(name)
                    .description("A " + scalar.javaClass().getSimpleName() + " in the format " + (format == null ? TimeScalars.ISO : format))
                    .coercing(TimeScalars.coercing(scalar, name, format)).build();
            return GraphQLScalarMapping.of(type, scalar.javaClass());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("The format '" + format + "' of " + ScalarSettings.PREFIX + scalar.id()
                    + ".format is not valid: " + e.getMessage(), e);
        }
    }

    private static GraphQLScalarMapping extended(StandardScalar scalar, String name) {
        GraphQLScalarType base = base(scalar);
        GraphQLScalarType type = base.getName().equals(name) ? base : GraphQLScalarType.newScalar(base).name(name).build();
        Class<?> javaClass = scalar.javaClass();
        if (javaClass == Integer.class || javaClass == Double.class) {
            return GraphQLScalarMapping.builder(type).outputClasses(Number.class).inputClasses(javaClass).build();
        }
        return GraphQLScalarMapping.of(type, javaClass);
    }

    private static GraphQLScalarType base(StandardScalar scalar) {
        switch (scalar) {
            case URL:
                return ExtendedScalars.Url;
            case UUID:
                return ExtendedScalars.UUID;
            case LOCALE:
                return ExtendedScalars.Locale;
            case CURRENCY:
                return ExtendedScalars.Currency;
            case POSITIVE_INT:
                return ExtendedScalars.PositiveInt;
            case NEGATIVE_INT:
                return ExtendedScalars.NegativeInt;
            case NON_POSITIVE_INT:
                return ExtendedScalars.NonPositiveInt;
            case NON_NEGATIVE_INT:
                return ExtendedScalars.NonNegativeInt;
            case POSITIVE_FLOAT:
                return ExtendedScalars.PositiveFloat;
            case NEGATIVE_FLOAT:
                return ExtendedScalars.NegativeFloat;
            case NON_POSITIVE_FLOAT:
                return ExtendedScalars.NonPositiveFloat;
            case NON_NEGATIVE_FLOAT:
                return ExtendedScalars.NonNegativeFloat;
            default:
                throw new IllegalArgumentException("not an extended scalar: " + scalar.id());
        }
    }

}
