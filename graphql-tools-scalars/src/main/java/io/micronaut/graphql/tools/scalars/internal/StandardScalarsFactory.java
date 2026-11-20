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
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.scalars.StandardScalar;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

/**
 * Registers a {@link GraphQLScalarMapping} bean for each scalar of {@link StandardScalar}. The master switch is
 * {@code micronaut.graphql.tools.scalars.enabled}; each scalar has an {@code enabled} switch of its own, which is why there is one
 * method for each: the condition is read before any bean of the scalar is created. A mapping only takes effect for a scalar that the
 * schema declares, so the beans of unused scalars do no work beyond their creation.
 */
@Internal
@Factory
@Requires(property = ScalarSettings.PREFIX + "enabled", notEquals = "false")
public final class StandardScalarsFactory {

    private final ScalarSettings settings;

    StandardScalarsFactory(ScalarSettings settings) {
        this.settings = settings;
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "instant.enabled", notEquals = "false")
    GraphQLScalarMapping instant() {
        return StandardMappings.create(StandardScalar.INSTANT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "local-date.enabled", notEquals = "false")
    GraphQLScalarMapping localDate() {
        return StandardMappings.create(StandardScalar.LOCAL_DATE, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "local-time.enabled", notEquals = "false")
    GraphQLScalarMapping localTime() {
        return StandardMappings.create(StandardScalar.LOCAL_TIME, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "local-date-time.enabled", notEquals = "false")
    GraphQLScalarMapping localDateTime() {
        return StandardMappings.create(StandardScalar.LOCAL_DATE_TIME, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "offset-time.enabled", notEquals = "false")
    GraphQLScalarMapping offsetTime() {
        return StandardMappings.create(StandardScalar.OFFSET_TIME, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "offset-date-time.enabled", notEquals = "false")
    GraphQLScalarMapping offsetDateTime() {
        return StandardMappings.create(StandardScalar.OFFSET_DATE_TIME, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "zoned-date-time.enabled", notEquals = "false")
    GraphQLScalarMapping zonedDateTime() {
        return StandardMappings.create(StandardScalar.ZONED_DATE_TIME, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "year-month.enabled", notEquals = "false")
    GraphQLScalarMapping yearMonth() {
        return StandardMappings.create(StandardScalar.YEAR_MONTH, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "year.enabled", notEquals = "false")
    GraphQLScalarMapping year() {
        return StandardMappings.create(StandardScalar.YEAR, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "duration.enabled", notEquals = "false")
    GraphQLScalarMapping duration() {
        return StandardMappings.create(StandardScalar.DURATION, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "period.enabled", notEquals = "false")
    GraphQLScalarMapping period() {
        return StandardMappings.create(StandardScalar.PERIOD, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "json.enabled", notEquals = "false")
    GraphQLScalarMapping json(BeanProvider<JsonMapper> mapper) {
        return StandardMappings.create(StandardScalar.JSON, settings, mapper::get);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "url.enabled", notEquals = "false")
    GraphQLScalarMapping url() {
        return StandardMappings.create(StandardScalar.URL, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "uuid.enabled", notEquals = "false")
    GraphQLScalarMapping uuid() {
        return StandardMappings.create(StandardScalar.UUID, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "locale.enabled", notEquals = "false")
    GraphQLScalarMapping locale() {
        return StandardMappings.create(StandardScalar.LOCALE, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "currency.enabled", notEquals = "false")
    GraphQLScalarMapping currency() {
        return StandardMappings.create(StandardScalar.CURRENCY, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "positive-int.enabled", notEquals = "false")
    GraphQLScalarMapping positiveInt() {
        return StandardMappings.create(StandardScalar.POSITIVE_INT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "negative-int.enabled", notEquals = "false")
    GraphQLScalarMapping negativeInt() {
        return StandardMappings.create(StandardScalar.NEGATIVE_INT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "non-positive-int.enabled", notEquals = "false")
    GraphQLScalarMapping nonPositiveInt() {
        return StandardMappings.create(StandardScalar.NON_POSITIVE_INT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "non-negative-int.enabled", notEquals = "false")
    GraphQLScalarMapping nonNegativeInt() {
        return StandardMappings.create(StandardScalar.NON_NEGATIVE_INT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "positive-float.enabled", notEquals = "false")
    GraphQLScalarMapping positiveFloat() {
        return StandardMappings.create(StandardScalar.POSITIVE_FLOAT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "negative-float.enabled", notEquals = "false")
    GraphQLScalarMapping negativeFloat() {
        return StandardMappings.create(StandardScalar.NEGATIVE_FLOAT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "non-positive-float.enabled", notEquals = "false")
    GraphQLScalarMapping nonPositiveFloat() {
        return StandardMappings.create(StandardScalar.NON_POSITIVE_FLOAT, settings, null);
    }

    @Bean
    @Singleton
    @Requires(property = ScalarSettings.PREFIX + "non-negative-float.enabled", notEquals = "false")
    GraphQLScalarMapping nonNegativeFloat() {
        return StandardMappings.create(StandardScalar.NON_NEGATIVE_FLOAT, settings, null);
    }

}
