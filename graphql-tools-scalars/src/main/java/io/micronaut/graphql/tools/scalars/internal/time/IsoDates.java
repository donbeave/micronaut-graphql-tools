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

import java.time.LocalDate;

/**
 * The common case of the date scalars, read without a formatter: {@code yyyy-MM-dd} allocates the date and nothing else.
 */
@Internal
final class IsoDates {

    private static final int LENGTH = 10;

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
