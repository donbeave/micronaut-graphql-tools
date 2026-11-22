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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.scalars.ScalarConfiguration;
import io.micronaut.graphql.tools.scalars.ScalarsConfiguration;
import io.micronaut.graphql.tools.scalars.StandardScalar;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/**
 * Registers a {@link GraphQLScalarMapping} bean for each scalar of {@link StandardScalar}. The master switch is
 * {@link ScalarsConfiguration#ENABLED_CONFIG}; a scalar whose {@link ScalarConfiguration#isEnabled()} is {@code false} is a
 * disabled bean, so it has no mapping. There is one method for each scalar because a method makes one bean. A mapping only takes
 * effect for a scalar that the schema declares, so the beans of unused scalars do no work beyond their creation.
 */
@Internal
@Factory
@Requires(property = ScalarsConfiguration.ENABLED_CONFIG, notEquals = "false")
public final class StandardScalarsFactory {

    private final ScalarsConfiguration configuration;

    StandardScalarsFactory(ScalarsConfiguration configuration) {
        this.configuration = configuration;
    }

    private GraphQLScalarMapping mapping(StandardScalar scalar, @Nullable Supplier<JsonMapper> mapper) {
        ScalarConfiguration scalarConfiguration = configuration.get(scalar);
        if (scalarConfiguration != null && !scalarConfiguration.isEnabled()) {
            throw new DisabledBeanException("The scalar " + scalar.id() + " is switched off by " + ScalarsConfiguration.PREFIX + "."
                    + scalar.id() + ".enabled");
        }
        return StandardMappings.create(scalar, scalarConfiguration, mapper);
    }

    @Singleton
    GraphQLScalarMapping instant() {
        return mapping(StandardScalar.INSTANT, null);
    }

    @Singleton
    GraphQLScalarMapping localDate() {
        return mapping(StandardScalar.LOCAL_DATE, null);
    }

    @Singleton
    GraphQLScalarMapping localTime() {
        return mapping(StandardScalar.LOCAL_TIME, null);
    }

    @Singleton
    GraphQLScalarMapping localDateTime() {
        return mapping(StandardScalar.LOCAL_DATE_TIME, null);
    }

    @Singleton
    GraphQLScalarMapping offsetTime() {
        return mapping(StandardScalar.OFFSET_TIME, null);
    }

    @Singleton
    GraphQLScalarMapping offsetDateTime() {
        return mapping(StandardScalar.OFFSET_DATE_TIME, null);
    }

    @Singleton
    GraphQLScalarMapping zonedDateTime() {
        return mapping(StandardScalar.ZONED_DATE_TIME, null);
    }

    @Singleton
    GraphQLScalarMapping yearMonth() {
        return mapping(StandardScalar.YEAR_MONTH, null);
    }

    @Singleton
    GraphQLScalarMapping year() {
        return mapping(StandardScalar.YEAR, null);
    }

    @Singleton
    GraphQLScalarMapping duration() {
        return mapping(StandardScalar.DURATION, null);
    }

    @Singleton
    GraphQLScalarMapping period() {
        return mapping(StandardScalar.PERIOD, null);
    }

    @Singleton
    GraphQLScalarMapping json(BeanProvider<JsonMapper> mapper) {
        return mapping(StandardScalar.JSON, mapper::get);
    }

    @Singleton
    GraphQLScalarMapping url() {
        return mapping(StandardScalar.URL, null);
    }

    @Singleton
    GraphQLScalarMapping uuid() {
        return mapping(StandardScalar.UUID, null);
    }

    @Singleton
    GraphQLScalarMapping locale() {
        return mapping(StandardScalar.LOCALE, null);
    }

    @Singleton
    GraphQLScalarMapping currency() {
        return mapping(StandardScalar.CURRENCY, null);
    }

    @Singleton
    GraphQLScalarMapping positiveInt() {
        return mapping(StandardScalar.POSITIVE_INT, null);
    }

    @Singleton
    GraphQLScalarMapping negativeInt() {
        return mapping(StandardScalar.NEGATIVE_INT, null);
    }

    @Singleton
    GraphQLScalarMapping nonPositiveInt() {
        return mapping(StandardScalar.NON_POSITIVE_INT, null);
    }

    @Singleton
    GraphQLScalarMapping nonNegativeInt() {
        return mapping(StandardScalar.NON_NEGATIVE_INT, null);
    }

    @Singleton
    GraphQLScalarMapping positiveFloat() {
        return mapping(StandardScalar.POSITIVE_FLOAT, null);
    }

    @Singleton
    GraphQLScalarMapping negativeFloat() {
        return mapping(StandardScalar.NEGATIVE_FLOAT, null);
    }

    @Singleton
    GraphQLScalarMapping nonPositiveFloat() {
        return mapping(StandardScalar.NON_POSITIVE_FLOAT, null);
    }

    @Singleton
    GraphQLScalarMapping nonNegativeFloat() {
        return mapping(StandardScalar.NON_NEGATIVE_FLOAT, null);
    }

}
