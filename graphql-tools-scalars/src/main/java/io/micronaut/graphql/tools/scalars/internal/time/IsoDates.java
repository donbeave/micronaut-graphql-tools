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

import io.micronaut.core.annotation.Internal;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The common cases of the date scalars, read without a formatter: {@code yyyy-MM-dd} allocates the date and nothing else, and an
 * instant of the shape {@code yyyy-MM-ddTHH:mm:ss[.fraction]Z} allocates the instant and nothing else. Any other text, and any text
 * with a field out of range, goes to the JDK parser, which accepts more shapes and reports the failure.
 */
@Internal
final class IsoDates {

    private static final int LENGTH = 10;
    private static final int[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final int MIN_INSTANT_LENGTH = 20;
    private static final int MAX_FRACTION_DIGITS = 9;
    private static final long DAYS_0000_TO_1970 = 719_528;
    private static final long SECONDS_PER_DAY = 86_400;
    private static final long MIN_WRITABLE_SECOND = -62_167_219_200L;
    private static final long MAX_WRITABLE_SECOND = 253_402_300_799L;
    private static final long DAYS_FROM_0000_TO_1970 = 719_468;
    private static final int DAYS_PER_ERA = 146_097;
    private static final int NANOS_PER_MILLI = 1_000_000;
    private static final int NANOS_PER_MICRO = 1000;

    private IsoDates() {
    }

    /**
     * @param text a date
     * @return the date; a text that is not exactly {@code yyyy-MM-dd} (an expanded year, for one) is read by {@link LocalDate#parse}
     */
    static LocalDate parseLocalDate(String text) {
        if (text.length() != LENGTH || text.charAt(4) != '-' || text.charAt(7) != '-') {
            return LocalDate.parse(text);
        }
        int year = digits(text, 0, 4);
        int month = digits(text, 5, 7);
        int day = digits(text, 8, 10);
        if (year < 0 || month < 0 || day < 0) {
            return LocalDate.parse(text);
        }
        return LocalDate.of(year, month, day);
    }

    /**
     * @param text an instant
     * @return the instant; a text that is not {@code yyyy-MM-ddTHH:mm:ss[.fraction]Z} with every field in range is read by {@link Instant#parse}
     */
    static Instant parseInstant(String text) {
        int length = text.length();
        if (length < MIN_INSTANT_LENGTH || length > MIN_INSTANT_LENGTH + 1 + MAX_FRACTION_DIGITS || text.charAt(4) != '-'
                || text.charAt(7) != '-' || text.charAt(10) != 'T' || text.charAt(13) != ':' || text.charAt(16) != ':'
                || text.charAt(length - 1) != 'Z') {
            return Instant.parse(text);
        }
        int year = digits(text, 0, 4);
        int month = digits(text, 5, 7);
        int day = digits(text, 8, 10);
        int hour = digits(text, 11, 13);
        int minute = digits(text, 14, 16);
        int second = digits(text, 17, 19);
        if (year < 0 || month < 1 || month > 12 || day < 1 || hour < 0 || hour > 23 || minute < 0 || minute > 59
                || second < 0 || second > 59 || day > daysInMonth(year, month)) {
            return Instant.parse(text);
        }
        int nanos = 0;
        if (length > MIN_INSTANT_LENGTH) {
            if (length == MIN_INSTANT_LENGTH + 1 || text.charAt(19) != '.') {
                return Instant.parse(text);
            }
            int digits = length - 21;
            nanos = digits(text, 20, length - 1);
            if (nanos < 0) {
                return Instant.parse(text);
            }
            for (int i = digits; i < MAX_FRACTION_DIGITS; i++) {
                nanos *= 10;
            }
        } else if (text.charAt(19) != 'Z') {
            return Instant.parse(text);
        }
        long epochDay = epochDay(year, month, day);
        return Instant.ofEpochSecond(epochDay * SECONDS_PER_DAY + hour * 3600L + minute * 60L + second, nanos);
    }

    /**
     * @param instant an instant
     * @return the text {@link Instant#toString()} gives: {@code yyyy-MM-ddTHH:mm:ss[.fraction]Z}, with the fraction in groups of
     * three digits; an instant outside the years 0 to 9999 is written by the JDK
     */
    static String writeInstant(Instant instant) {
        long seconds = instant.getEpochSecond();
        if (seconds < MIN_WRITABLE_SECOND || seconds > MAX_WRITABLE_SECOND) {
            return instant.toString();
        }
        long epochDay = Math.floorDiv(seconds, SECONDS_PER_DAY);
        int secondOfDay = (int) Math.floorMod(seconds, SECONDS_PER_DAY);
        long z = epochDay + DAYS_FROM_0000_TO_1970;
        long era = Math.floorDiv(z, (long) DAYS_PER_ERA);
        int dayOfEra = (int) (z - era * DAYS_PER_ERA);
        int yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
        int dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        int shiftedMonth = (5 * dayOfYear + 2) / 153;
        int day = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1;
        int month = shiftedMonth < 10 ? shiftedMonth + 3 : shiftedMonth - 9;
        int year = (int) (yearOfEra + era * 400) + (month <= 2 ? 1 : 0);
        int nanos = instant.getNano();
        int fraction = nanos == 0 ? 0 : nanos % NANOS_PER_MILLI == 0 ? 3 : nanos % NANOS_PER_MICRO == 0 ? 6 : MAX_FRACTION_DIGITS;
        char[] out = new char[MIN_INSTANT_LENGTH + (fraction == 0 ? 0 : fraction + 1)];
        put(out, 0, year, 4);
        out[4] = '-';
        put(out, 5, month, 2);
        out[7] = '-';
        put(out, 8, day, 2);
        out[10] = 'T';
        put(out, 11, secondOfDay / 3600, 2);
        out[13] = ':';
        put(out, 14, secondOfDay / 60 % 60, 2);
        out[16] = ':';
        put(out, 17, secondOfDay % 60, 2);
        if (fraction != 0) {
            out[19] = '.';
            int scaled = nanos;
            for (int i = fraction; i < MAX_FRACTION_DIGITS; i++) {
                scaled /= 10;
            }
            put(out, 20, scaled, fraction);
        }
        out[out.length - 1] = 'Z';
        return new String(out);
    }

    private static void put(char[] out, int offset, int value, int width) {
        int rest = value;
        for (int i = offset + width - 1; i >= offset; i--) {
            out[i] = (char) ('0' + rest % 10);
            rest /= 10;
        }
    }

    private static boolean isLeap(int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    private static int daysInMonth(int year, int month) {
        return month == 2 && isLeap(year) ? 29 : DAYS_IN_MONTH[month - 1];
    }

    /** The algorithm of {@link LocalDate#toEpochDay()}, for a year of 0 to 9999 and a month and day in range. */
    private static long epochDay(int year, int month, int day) {
        long total = 365L * year + (year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400 + (367 * month - 362) / 12 + day - 1;
        if (month > 2) {
            total--;
            if (!isLeap(year)) {
                total--;
            }
        }
        return total - DAYS_0000_TO_1970;
    }

    private static int digits(String text, int from, int to) {
        int result = 0;
        for (int i = from; i < to; i++) {
            int digit = text.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                return -1;
            }
            result = result * 10 + digit;
        }
        return result;
    }

}
