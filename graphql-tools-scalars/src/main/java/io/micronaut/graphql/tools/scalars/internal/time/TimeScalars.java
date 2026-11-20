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
package io.micronaut.graphql.tools.scalars.internal.time;

import graphql.schema.Coercing;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.scalars.StandardScalar;

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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalQuery;
import java.util.Locale;

/**
 * Builds the coercion of a {@code java.time} scalar from its format: {@code iso} (the default), a keyword of the scalar
 * ({@code epoch-seconds}, {@code epoch-millis}, {@code seconds}, {@code millis}) or a {@link DateTimeFormatter} pattern.
 * A pattern is read with {@link Locale#ROOT}, and an {@code Instant} pattern is read and written in UTC.
 */
@Internal
public final class TimeScalars {

    /** The value of the format that selects ISO-8601. */
    public static final String ISO = "iso";

    private TimeScalars() {
    }

    /**
     * @param scalar a scalar of the group {@link StandardScalar.Group#JAVA_TIME}
     * @param name   the name of the scalar in the schema
     * @param format the wire format; {@code null} is the default
     * @return the coercion
     * @throws IllegalArgumentException when the format is not valid for the scalar
     */
    public static Coercing<?, ?> coercing(StandardScalar scalar, String name, String format) {
        String wanted = format == null ? ISO : format;
        if (!scalar.isFormatConfigurable() && !ISO.equalsIgnoreCase(wanted)) {
            throw new IllegalArgumentException("the scalar " + scalar.id() + " has one format and cannot be configured");
        }
        boolean iso = ISO.equalsIgnoreCase(wanted);
        switch (scalar) {
            case INSTANT:
                return instant(name, wanted, iso);
            case LOCAL_DATE:
                return iso ? new TextCoercing<>(name, LocalDate.class, IsoDates::parseLocalDate, LocalDate::toString,
                        "ISO-8601 yyyy-MM-dd") : pattern(name, LocalDate.class, wanted, LocalDate::from);
            case LOCAL_TIME:
                return iso ? TextCoercing.of(name, LocalTime.class, DateTimeFormatter.ISO_LOCAL_TIME, LocalTime::from,
                        "ISO-8601 HH:mm:ss") : pattern(name, LocalTime.class, wanted, LocalTime::from);
            case LOCAL_DATE_TIME:
                return iso ? TextCoercing.of(name, LocalDateTime.class, DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        LocalDateTime::from, "ISO-8601 yyyy-MM-dd'T'HH:mm:ss") : pattern(name, LocalDateTime.class, wanted, LocalDateTime::from);
            case OFFSET_TIME:
                return iso ? TextCoercing.of(name, OffsetTime.class, DateTimeFormatter.ISO_OFFSET_TIME, OffsetTime::from,
                        "ISO-8601 HH:mm:ssXXX") : pattern(name, OffsetTime.class, wanted, OffsetTime::from);
            case OFFSET_DATE_TIME:
                return iso ? TextCoercing.of(name, OffsetDateTime.class, DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                        OffsetDateTime::from, "ISO-8601 yyyy-MM-dd'T'HH:mm:ssXXX") : pattern(name, OffsetDateTime.class, wanted, OffsetDateTime::from);
            case ZONED_DATE_TIME:
                return iso ? TextCoercing.of(name, ZonedDateTime.class, DateTimeFormatter.ISO_ZONED_DATE_TIME,
                        ZonedDateTime::from, "ISO-8601 yyyy-MM-dd'T'HH:mm:ssXXX[zone]") : pattern(name, ZonedDateTime.class, wanted, ZonedDateTime::from);
            case YEAR_MONTH:
                return iso ? new TextCoercing<>(name, YearMonth.class, YearMonth::parse, YearMonth::toString, "ISO-8601 yyyy-MM")
                        : pattern(name, YearMonth.class, wanted, YearMonth::from);
            case YEAR:
                return iso ? new TextCoercing<>(name, Year.class, Year::parse, Year::toString, "ISO-8601 yyyy")
                        : pattern(name, Year.class, wanted, Year::from);
            case DURATION:
                return duration(name, wanted, iso);
            case PERIOD:
                return new TextCoercing<>(name, Period.class, Period::parse, Period::toString, "ISO-8601 PnYnMnD");
            default:
                throw new IllegalArgumentException("not a java.time scalar: " + scalar.id());
        }
    }

    private static Coercing<?, ?> instant(String name, String format, boolean iso) {
        if (iso) {
            return new TextCoercing<>(name, Instant.class, IsoDates::parseInstant, IsoDates::writeInstant, "ISO-8601 yyyy-MM-dd'T'HH:mm:ssZ");
        }
        if ("epoch-seconds".equalsIgnoreCase(format)) {
            return new CountCoercing<>(name, Instant.class, Instant::getEpochSecond, Instant::ofEpochSecond,
                    "a whole number of seconds since the epoch");
        }
        if ("epoch-millis".equalsIgnoreCase(format)) {
            return new CountCoercing<>(name, Instant.class, Instant::toEpochMilli, Instant::ofEpochMilli,
                    "a whole number of milliseconds since the epoch");
        }
        DateTimeFormatter formatter = formatter(format).withZone(ZoneOffset.UTC);
        return TextCoercing.of(name, Instant.class, formatter, Instant::from, "the pattern " + format + " (UTC)");
    }

    private static Coercing<?, ?> duration(String name, String format, boolean iso) {
        if (iso) {
            return new TextCoercing<>(name, Duration.class, Duration::parse, Duration::toString, "ISO-8601 PTnHnMnS");
        }
        if ("seconds".equalsIgnoreCase(format)) {
            return new CountCoercing<>(name, Duration.class, Duration::getSeconds, Duration::ofSeconds,
                    "a whole number of seconds");
        }
        if ("millis".equalsIgnoreCase(format)) {
            return new CountCoercing<>(name, Duration.class, Duration::toMillis, Duration::ofMillis,
                    "a whole number of milliseconds");
        }
        throw new IllegalArgumentException("the format of a Duration is iso, seconds or millis, not " + format);
    }

    private static <T> Coercing<?, ?> pattern(String name, Class<T> type, String format,
                                              TemporalQuery<T> query) {
        return TextCoercing.of(name, type, formatter(format), query, "the pattern " + format);
    }

    /**
     * A pattern is resolved strictly, so that a value such as 31/02/2026 is refused instead of clamped to the end of the month.
     * Strict resolution needs the proleptic year {@code u}, as {@code y} (year of era) cannot resolve a date without an era, so
     * the {@code y} letters of the pattern, outside quotes, are read as {@code u}: the same digits for every year since 1 AD.
     * A pattern with an era letter ({@code G}) keeps its {@code y}, as the era makes it resolvable.
     */
    private static boolean unquoted(String format, char letter) {
        boolean quoted = false;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == letter) {
                return true;
            }
        }
        return false;
    }

    private static DateTimeFormatter formatter(String format) {
        boolean era = unquoted(format, 'G');
        StringBuilder pattern = new StringBuilder(format.length());
        boolean quoted = false;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            }
            pattern.append(!quoted && !era && c == 'y' ? 'u' : c);
        }
        return DateTimeFormatter.ofPattern(pattern.toString(), Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

}
