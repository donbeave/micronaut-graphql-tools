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
package io.micronaut.graphql.tools.binding.internal.schema;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.PathMatcher;

/**
 * Where the runtime looks for schema files: {@code classpath:} (the first classpath entry that holds the directory),
 * {@code classpath*:} (every entry, for dependency jars) or {@code file:}, followed by an Ant-style path.
 *
 * @param scheme  where the path is resolved
 * @param pattern the path, with {@code *}, {@code **} and {@code ?} as wildcards, without a leading slash for the classpath
 */
@Internal
public record SchemaLocation(Scheme scheme, String pattern) {

    /**
     * The default location of the schema files.
     */
    public static final String DEFAULT = "classpath:graphql/**/*" + SchemaSources.EXTENSION;

    /**
     * Where a path is resolved.
     */
    public enum Scheme {

        /** The first classpath entry that holds the directory. */
        CLASSPATH("classpath:"),

        /** Every classpath entry that holds the directory. */
        CLASSPATH_ALL("classpath*:"),

        /** The file system. */
        FILE("file:");

        private final String prefix;

        Scheme(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    /**
     * Parses a location.
     *
     * @param location for example {@code classpath*:graphql/**}{@code /*.graphqls}
     * @return the location
     * @throws IllegalArgumentException when the location has no known scheme, or has a wildcard without a directory before it
     */
    public static SchemaLocation parse(String location) {
        String text = location.trim();
        for (Scheme scheme : Scheme.values()) {
            if (text.startsWith(scheme.prefix())) {
                String path = text.substring(scheme.prefix().length());
                if (scheme == Scheme.FILE) {
                    path = path.replace('\\', '/');
                } else {
                    path = path.replaceFirst("^/+", "");
                }
                SchemaLocation result = new SchemaLocation(scheme, path);
                if (path.isBlank()) {
                    throw new IllegalArgumentException("'" + location + "' names no path");
                }
                if (result.isPattern() && result.root().isEmpty()) {
                    throw new IllegalArgumentException("'" + location + "' has a wildcard but no directory before it; start "
                            + "with a directory, as in " + DEFAULT);
                }
                return result;
            }
        }
        throw new IllegalArgumentException("'" + location + "' must start with classpath:, classpath*: or file:");
    }

    /**
     * @return whether the path has wildcards, so that the files are found by listing a directory
     */
    public boolean isPattern() {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
    }

    /**
     * @return the directory to list: the path up to the last slash before the first wildcard, with a trailing slash, or
     * empty when there is none
     */
    public String root() {
        int wildcard = pattern.length();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' || c == '?') {
                wildcard = i;
                break;
            }
        }
        int slash = pattern.lastIndexOf('/', wildcard);
        return slash < 0 ? "" : pattern.substring(0, slash + 1);
    }

    /**
     * @param path a path in the same form as the pattern
     * @return whether the path is one of the files of this location
     */
    public boolean matches(String path) {
        return isPattern() ? PathMatcher.ANT.matches(pattern, path) : pattern.equals(path);
    }

    @Override
    public String toString() {
        return scheme.prefix() + pattern;
    }
}
