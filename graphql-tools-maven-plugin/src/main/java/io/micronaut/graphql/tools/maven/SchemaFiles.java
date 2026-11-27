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
package io.micronaut.graphql.tools.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Selects the schema files of a schema directory: the files that match an include glob and no exclude glob, relative to the
 * directory, in the order of their relative path ({@code /} separators, UTF-8 byte order), so the order does not depend on the
 * file system.
 */
final class SchemaFiles {

    private SchemaFiles() {
    }

    static List<Path> select(Path directory, List<String> includes, List<String> excludes) {
        List<PathMatcher> include = matchers(includes);
        List<PathMatcher> exclude = matchers(excludes);
        List<Path> selected = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                Path relative = directory.relativize(file);
                if (matches(include, relative) && !matches(exclude, relative)) {
                    selected.add(file);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        selected.sort(Comparator.comparing((Path file) -> relative(directory, file), SchemaFiles::compareUtf8));
        return selected;
    }

    static String relative(Path directory, Path file) {
        return directory.relativize(file).toString().replace('\\', '/');
    }

    private static int compareUtf8(String a, String b) {
        return java.util.Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** A pattern that starts with {@code **}{@code /} also selects the files directly in the directory, as it does in an Ant or a Gradle pattern. */
    private static List<PathMatcher> matchers(List<String> globs) {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
            if (glob.startsWith("**/")) {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob.substring(3)));
            }
        }
        return matchers;
    }

    private static boolean matches(List<PathMatcher> matchers, Path relative) {
        return matchers.stream().anyMatch(matcher -> matcher.matches(relative));
    }
}
