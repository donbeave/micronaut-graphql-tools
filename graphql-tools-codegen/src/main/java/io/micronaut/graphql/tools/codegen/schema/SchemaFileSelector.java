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
package io.micronaut.graphql.tools.codegen.schema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Selects the schema files of a directory with the rules of the options {@code includes} and {@code excludes}, so that a build
 * plugin and the generator select the same files. A glob matches the path relative to the directory with {@code /} separators:
 * {@code *} is any characters but {@code /}, {@code ?} is one such character, {@code **} is any characters including {@code /},
 * {@code **}{@code /} is any number of directories (also none), {@code {a,b}} is an alternative, and every other character,
 * {@code [} and {@code ]} included, stands for itself.
 */
public final class SchemaFileSelector {

    private SchemaFileSelector() {
    }

    /**
     * Lists the regular files below a directory that match an include glob and no exclude glob.
     *
     * @param directory the schema root; it must exist
     * @param includes  the include globs
     * @param excludes  the exclude globs
     * @return the files, sorted by their relative path in UTF-8 byte order
     * @throws UncheckedIOException when the directory or a subdirectory cannot be listed
     */
    public static List<Path> select(Path directory, List<String> includes, List<String> excludes) {
        List<Pattern> include = Globs.compile(includes);
        List<Pattern> exclude = Globs.compile(excludes);
        List<Path> selected = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String relative = relative(directory, file);
                if (Globs.matchesAny(include, relative) && !Globs.matchesAny(exclude, relative)) {
                    selected.add(file);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        selected.sort((a, b) -> SchemaLoader.UTF8_ORDER.compare(relative(directory, a), relative(directory, b)));
        return selected;
    }

    static String relative(Path root, Path file) {
        StringBuilder path = new StringBuilder();
        for (Path part : root.relativize(file)) {
            path.append(path.length() == 0 ? "" : "/").append(part);
        }
        return path.toString();
    }
}
