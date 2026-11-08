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

/**
 * The options of {@link GraphQLSchemaBindingProcessor}, passed as {@code -A<name>=<value>}. Except for
 * {@link #SDL_DIR}, each has the values and the default of the runtime option of the same name under
 * {@code micronaut.graphql.tools}, so that one configuration means the same at compile time and at startup. The names
 * are written in camel case ({@code allowUnreachable} for {@code allow-unreachable}) because javac accepts only
 * dot-separated identifiers as option names; Micronaut accepts both spellings for the runtime options.
 *
 * @param sdlDir             the directory of the schema files, or {@code null} if not set
 * @param strict             whether strict mode is on
 * @param precedence         how a field that a property and a type resolver method can both bind is bound
 * @param resolverDirectives the directives that mark a field as bound by a type resolver method
 * @param allowUnreachable   the unreachable types that are allowed
 * @param ignoreUnreachable  whether unreachable types are ignored ({@code unreachable-types=ignore})
 * @param unmappedProperties how input class properties without an input field are handled: {@code fail},
 *                           {@code ignore} or {@code null} to follow strict mode
 */
record ProcessorOptions(String sdlDir,
                        boolean strict,
                        Precedence precedence,
                        List<String> resolverDirectives,
                        Set<String> allowUnreachable,
                        boolean ignoreUnreachable,
                        String unmappedProperties) {

    /**
     * The prefix of every option.
     */
    static final String PREFIX = "micronaut.graphql.tools.";

    /**
     * The directory of the schema files ({@code *.graphqls}, {@code *.graphql}), read recursively. Required when the
     * compilation has anything to check. Declare it as an input of the compile task (for Gradle, a
     * {@code CommandLineArgumentProvider} with {@code @InputDirectory}), or a schema change does not recompile.
     */
    static final String SDL_DIR = PREFIX + "sdlDir";

    /**
     * Strict mode, {@code true} or {@code false}; default {@code true}.
     */
    static final String STRICT = PREFIX + "strict";

    /**
     * {@code strict}, {@code property-first} or {@code resolver-first}; default {@code strict}.
     */
    static final String PRECEDENCE = PREFIX + "precedence";

    /**
     * Comma-separated directive names; default none.
     */
    static final String RESOLVER_DIRECTIVES = PREFIX + "resolverDirectives";

    /**
     * Comma-separated type names; default none.
     */
    static final String ALLOW_UNREACHABLE = PREFIX + "allowUnreachable";

    /**
     * {@code fail} or {@code ignore}; default {@code fail}.
     */
    static final String UNREACHABLE_TYPES = PREFIX + "unreachableTypes";

    /**
     * {@code fail} or {@code ignore}; default: not set, which follows strict mode.
     */
    static final String UNMAPPED_PROPERTIES = PREFIX + "input.unmappedProperties";

    /**
     * Every option name.
     */
    static final Set<String> NAMES = Set.of(SDL_DIR, STRICT, PRECEDENCE, RESOLVER_DIRECTIVES, ALLOW_UNREACHABLE,
            UNREACHABLE_TYPES, UNMAPPED_PROPERTIES);

    /**
     * How a field is bound when both a member of its Java type and a type resolver method can bind it.
     */
    enum Precedence {

        /**
         * Neither: an error.
         */
        STRICT,

        /**
         * The member of the Java type.
         */
        PROPERTY_FIRST,

        /**
         * The type resolver method.
         */
        RESOLVER_FIRST
    }

    /**
     * Reads the options.
     *
     * @param options  the processor options
     * @param problems where invalid values are reported
     * @return the options, with defaults for invalid values
     */
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

        String sdlDir = options.get(SDL_DIR);
        return new ProcessorOptions(
                sdlDir == null || sdlDir.isBlank() ? null : sdlDir.trim(),
                strict,
                precedence,
                list(options.get(RESOLVER_DIRECTIVES)),
                new TreeSet<>(list(options.get(ALLOW_UNREACHABLE))),
                unreachableTypes.equals("ignore"),
                unmappedProperties
        );
    }

    /**
     * @return whether input class properties without an input field fail the compilation
     */
    boolean failOnUnmappedProperties() {
        return unmappedProperties == null ? strict : unmappedProperties.equals("fail");
    }

    /**
     * Reads a boolean option as Micronaut converts a boolean property: {@code true}, {@code yes}, {@code y} and
     * {@code on} are true, {@code false}, {@code no}, {@code n} and {@code off} are false, in any case. Micronaut
     * reads any other value as false; here it is an error, so that a typo does not silently switch a check off.
     */
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
