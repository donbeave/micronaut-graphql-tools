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
package io.micronaut.graphql.tools.processing;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

record ProcessorOptions(String sdlDir,
                        boolean strict,
                        Precedence precedence,
                        List<String> resolverDirectives,
                        Set<String> allowUnreachable,
                        boolean ignoreUnreachable,
                        String unmappedProperties,
                        boolean detectOmission,
                        boolean manifest) {

    static final String PREFIX = "micronaut.graphql.tools.";

    static final String SDL_DIR = PREFIX + "sdlDir";

    static final String STRICT = PREFIX + "strict";

    static final String PRECEDENCE = PREFIX + "precedence";

    static final String RESOLVER_DIRECTIVES = PREFIX + "resolverDirectives";

    static final String ALLOW_UNREACHABLE = PREFIX + "allowUnreachable";

    static final String UNREACHABLE_TYPES = PREFIX + "unreachableTypes";

    static final String UNMAPPED_PROPERTIES = PREFIX + "input.unmappedProperties";

    static final String DETECT_OMISSION = PREFIX + "input.detectOmission";

    static final String MANIFEST = PREFIX + "manifest";

    static final Set<String> NAMES = Set.of(SDL_DIR, STRICT, PRECEDENCE, RESOLVER_DIRECTIVES, ALLOW_UNREACHABLE,
            UNREACHABLE_TYPES, UNMAPPED_PROPERTIES, DETECT_OMISSION, MANIFEST);

    enum Precedence {

        STRICT,

        PROPERTY_FIRST,

        RESOLVER_FIRST
    }

    static ProcessorOptions read(Map<String, String> options, Problems problems) {
        options.keySet().stream()
                .filter(it -> it.startsWith(PREFIX) && !NAMES.contains(it))
                .sorted()
                .forEach(it -> problems.error("unknown option -A" + it + ". The options are " + new TreeSet<>(NAMES)
                        + "."));

        boolean strict = bool(options, STRICT, true, problems);

        Precedence precedence = Precedence.STRICT;
        String precedenceValue = options.get(PRECEDENCE);
        if (precedenceValue != null) {
            try {
                precedence = Precedence.valueOf(precedenceValue.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                problems.error(PRECEDENCE + " must be strict, property-first or resolver-first, not "
                        + precedenceValue + ".");
            }
        }

        String unreachableTypes = options.getOrDefault(UNREACHABLE_TYPES, "fail").trim().toLowerCase(Locale.ROOT);
        if (!unreachableTypes.equals("fail") && !unreachableTypes.equals("ignore")) {
            problems.error(UNREACHABLE_TYPES + " must be fail or ignore, not " + options.get(UNREACHABLE_TYPES) + ".");
        }

        String unmappedProperties = options.get(UNMAPPED_PROPERTIES);
        if (unmappedProperties != null) {
            unmappedProperties = unmappedProperties.trim().toLowerCase(Locale.ROOT);
            if (!unmappedProperties.equals("fail") && !unmappedProperties.equals("ignore")) {
                problems.error(UNMAPPED_PROPERTIES + " must be fail or ignore, not " + options.get(UNMAPPED_PROPERTIES)
                        + ".");
                unmappedProperties = null;
            }
        }

        boolean manifest = bool(options, MANIFEST, true, problems);

        String sdlDir = options.get(SDL_DIR);
        return new ProcessorOptions(
                sdlDir == null || sdlDir.isBlank() ? null : sdlDir.trim(),
                strict,
                precedence,
                list(options.get(RESOLVER_DIRECTIVES)),
                new TreeSet<>(list(options.get(ALLOW_UNREACHABLE))),
                unreachableTypes.equals("ignore"),
                unmappedProperties,
                bool(options, DETECT_OMISSION, false, problems),
                manifest
        );
    }

    boolean failOnUnmappedProperties() {
        return unmappedProperties == null ? strict : unmappedProperties.equals("fail");
    }

    private static boolean bool(Map<String, String> options, String name, boolean defaultValue, Problems problems) {
        String value = options.get(name);
        if (value == null) {
            return defaultValue;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "on":
                return true;
            case "false", "no", "n", "off":
                return false;
            default:
                problems.error(name + " must be true or false (or yes/no, on/off), not " + value + ".");
                return defaultValue;
        }
    }

    private static List<String> list(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .distinct()
                .toList();
    }

}
