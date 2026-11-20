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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * The scalars of this module. Each constant has an id, the scalar's default name in the schema, the Java class that
 * stands for it and, for the scalars with a configurable wire format, the default format. The id is the key under which
 * the scalar is configured: {@code micronaut.graphql.tools.scalars.<id>.name}, {@code ...<id>.format} and
 * {@code ...<id>.enabled}.
 *
 * @since 1.0.0
 */
public enum StandardScalar {

    /** {@link Instant}, ISO-8601 by default; also {@code epoch-seconds} and {@code epoch-millis}. */
    INSTANT("instant", "Instant", Instant.class, Group.JAVA_TIME, "iso"),
    /** {@link LocalDate}. */
    LOCAL_DATE("local-date", "LocalDate", LocalDate.class, Group.JAVA_TIME, "iso"),
    /** {@link LocalTime}. */
    LOCAL_TIME("local-time", "LocalTime", LocalTime.class, Group.JAVA_TIME, "iso"),
    /** {@link LocalDateTime}. */
    LOCAL_DATE_TIME("local-date-time", "LocalDateTime", LocalDateTime.class, Group.JAVA_TIME, "iso"),
    /** {@link OffsetTime}. */
    OFFSET_TIME("offset-time", "OffsetTime", OffsetTime.class, Group.JAVA_TIME, "iso"),
    /** {@link OffsetDateTime}. */
    OFFSET_DATE_TIME("offset-date-time", "OffsetDateTime", OffsetDateTime.class, Group.JAVA_TIME, "iso"),
    /** {@link ZonedDateTime}. */
    ZONED_DATE_TIME("zoned-date-time", "ZonedDateTime", ZonedDateTime.class, Group.JAVA_TIME, "iso"),
    /** {@link YearMonth}, {@code uuuu-MM} by default. */
    YEAR_MONTH("year-month", "YearMonth", YearMonth.class, Group.JAVA_TIME, "iso"),
    /** {@link Year}, {@code uuuu} by default. */
    YEAR("year", "Year", Year.class, Group.JAVA_TIME, "iso"),
    /** {@link Duration}, ISO-8601 by default; also {@code seconds} and {@code millis}. */
    DURATION("duration", "Duration", Duration.class, Group.JAVA_TIME, "iso"),
    /** {@link Period}, ISO-8601 only. */
    PERIOD("period", "Period", Period.class, Group.JAVA_TIME, null),

    /** Any JSON value, converted with the application's {@code JsonMapper}. */
    JSON("json", "JSON", Object.class, Group.JSON, null),

    /** {@link URL}. */
    URL("url", "Url", URL.class, Group.EXTENDED, null),
    /** {@link UUID}. */
    UUID("uuid", "UUID", UUID.class, Group.EXTENDED, null),
    /** {@link Locale}. */
    LOCALE("locale", "Locale", Locale.class, Group.EXTENDED, null),
    /** {@link Currency}. */
    CURRENCY("currency", "Currency", Currency.class, Group.EXTENDED, null),
    /** An {@code Int} greater than zero. */
    POSITIVE_INT("positive-int", "PositiveInt", Integer.class, Group.EXTENDED, null),
    /** An {@code Int} less than zero. */
    NEGATIVE_INT("negative-int", "NegativeInt", Integer.class, Group.EXTENDED, null),
    /** An {@code Int} of zero or less. */
    NON_POSITIVE_INT("non-positive-int", "NonPositiveInt", Integer.class, Group.EXTENDED, null),
    /** An {@code Int} of zero or more. */
    NON_NEGATIVE_INT("non-negative-int", "NonNegativeInt", Integer.class, Group.EXTENDED, null),
    /** A {@code Float} greater than zero. */
    POSITIVE_FLOAT("positive-float", "PositiveFloat", Double.class, Group.EXTENDED, null),
    /** A {@code Float} less than zero. */
    NEGATIVE_FLOAT("negative-float", "NegativeFloat", Double.class, Group.EXTENDED, null),
    /** A {@code Float} of zero or less. */
    NON_POSITIVE_FLOAT("non-positive-float", "NonPositiveFloat", Double.class, Group.EXTENDED, null),
    /** A {@code Float} of zero or more. */
    NON_NEGATIVE_FLOAT("non-negative-float", "NonNegativeFloat", Double.class, Group.EXTENDED, null);

    /** Where a scalar comes from. */
    public enum Group {
        /** A {@code java.time} scalar of this module, with a configurable wire format. */
        JAVA_TIME,
        /** The JSON scalar of this module. */
        JSON,
        /** A scalar of graphql-java-extended-scalars, renamed on request. */
        EXTENDED
    }

    private final String id;
    private final String defaultName;
    private final Class<?> javaClass;
    private final Group group;
    private final String defaultFormat;

    StandardScalar(String id, String defaultName, Class<?> javaClass, Group group, String defaultFormat) {
        this.id = id;
        this.defaultName = defaultName;
        this.javaClass = javaClass;
        this.group = group;
        this.defaultFormat = defaultFormat;
    }

    /**
     * @return the id, the key of the scalar in the configuration: a lower-case kebab-case word
     */
    @NonNull
    public String id() {
        return id;
    }

    /**
     * @return the name of the scalar in the schema when the configuration does not rename it
     */
    @NonNull
    public String defaultName() {
        return defaultName;
    }

    /**
     * @return the Java class that stands for the scalar: the class of a field, argument or input field of this scalar
     */
    @NonNull
    public Class<?> javaClass() {
        return javaClass;
    }

    /**
     * @return where the scalar comes from
     */
    @NonNull
    public Group group() {
        return group;
    }

    /**
     * @return the default wire format ({@code iso}), or {@code null} when the scalar has one format that cannot be configured
     */
    @Nullable
    public String defaultFormat() {
        return defaultFormat;
    }

    /**
     * @return whether {@code micronaut.graphql.tools.scalars.<id>.format} changes the wire format of the scalar
     */
    public boolean isFormatConfigurable() {
        return defaultFormat != null;
    }
}
