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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.GraphQLSession.Cookie;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The cookies of one session, by name and path. Domain, Secure and HttpOnly are not interpreted: a session sends to one client id.
 */
@Internal
final class CookieJar {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.ENGLISH),
            twoDigitYear("EEE, dd-MMM-"),
            twoDigitYear("EEEE, dd-MMM-"));

    private static final Pattern DELTA = Pattern.compile("-?(0|[1-9][0-9]*)");

    private final Clock clock;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    CookieJar(Clock clock) {
        this.clock = clock;
    }

    synchronized void clear() {
        cookies.clear();
    }

    synchronized List<Cookie> snapshot() {
        return List.copyOf(live());
    }

    @Nullable
    synchronized String header(String requestPath) {
        List<Cookie> matching = new ArrayList<>();
        for (Cookie cookie : live()) {
            if (pathMatches(cookie.path(), requestPath)) {
                matching.add(cookie);
            }
        }
        if (matching.isEmpty()) {
            return null;
        }
        matching.sort(Comparator.comparingInt((Cookie cookie) -> cookie.path().length()).reversed());
        StringBuilder header = new StringBuilder();
        for (Cookie cookie : matching) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(cookie.name()).append('=').append(cookie.value());
        }
        return header.toString();
    }

    synchronized void store(List<String> setCookies, String requestPath) {
        Instant now = clock.instant();
        for (String line : setCookies) {
            parse(line, requestPath, now);
        }
    }

    private List<Cookie> live() {
        Instant now = clock.instant();
        cookies.values().removeIf(cookie -> cookie.expires() != null && !cookie.expires().isAfter(now));
        return new ArrayList<>(cookies.values());
    }

    private void parse(String line, String requestPath, Instant now) {
        String[] parts = line.split(";");
        int equals = parts[0].indexOf('=');
        if (equals <= 0) {
            return;
        }
        String name = parts[0].substring(0, equals).trim();
        String value = parts[0].substring(equals + 1).trim();
        if (name.isEmpty()) {
            return;
        }
        String path = defaultPath(requestPath);
        Instant expires = null;
        boolean maxAge = false;
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            int split = attribute.indexOf('=');
            String key = (split < 0 ? attribute : attribute.substring(0, split)).trim().toLowerCase(Locale.ROOT);
            String attributeValue = split < 0 ? "" : attribute.substring(split + 1).trim();
            switch (key) {
                case "path" -> {
                    if (attributeValue.startsWith("/")) {
                        path = attributeValue;
                    }
                }
                case "max-age" -> {
                    Instant parsed = maxAge(attributeValue, now);
                    if (parsed != null) {
                        expires = parsed;
                        maxAge = true;
                    }
                }
                case "expires" -> {
                    if (!maxAge) {
                        Instant parsed = date(attributeValue);
                        if (parsed != null) {
                            expires = parsed;
                        }
                    }
                }
                default -> {
                }
            }
        }
        String key = name + '\u0000' + path;
        if (expires != null && !expires.isAfter(now)) {
            cookies.remove(key);
        } else {
            cookies.put(key, new Cookie(name, value, path, expires));
        }
    }

    @Nullable
    private static Instant maxAge(String value, Instant now) {
        if (!DELTA.matcher(value).matches()) {
            return null;
        }
        if (value.startsWith("-")) {
            return Instant.MIN;
        }
        try {
            long seconds = Long.parseLong(value);
            return seconds == 0 ? Instant.MIN : now.plusSeconds(seconds);
        } catch (NumberFormatException | ArithmeticException | DateTimeException e) {
            return Instant.MAX;
        }
    }

    @Nullable
    private static Instant date(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(value, format).toInstant();
            } catch (DateTimeParseException e) {
                continue;
            }
        }
        return null;
    }

    private static DateTimeFormatter twoDigitYear(String prefix) {
        return new DateTimeFormatterBuilder().appendPattern(prefix).appendValueReduced(ChronoField.YEAR, 2, 2, 1970)
                .appendPattern(" HH:mm:ss zzz").toFormatter(Locale.ENGLISH);
    }

    private static String defaultPath(String requestPath) {
        int last = requestPath.lastIndexOf('/');
        return last <= 0 ? "/" : requestPath.substring(0, last);
    }

    private static boolean pathMatches(String cookiePath, String requestPath) {
        String path = requestPath.isEmpty() ? "/" : requestPath;
        if (path.equals(cookiePath)) {
            return true;
        }
        return path.startsWith(cookiePath) && (cookiePath.endsWith("/") || path.charAt(cookiePath.length()) == '/');
    }
}
