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

import java.util.ArrayList;
import java.util.List;

/**
 * The list of the schema files of a module, one classpath path per line, for the runtime that cannot list a directory of the
 * classpath (a native image). A line that starts with {@code #} and a blank line are ignored.
 */
@Internal
public final class SchemaIndex {

    /**
     * Where the index is on the classpath. A jar that provides schema files ships its own.
     */
    public static final String RESOURCE = "META-INF/micronaut-graphql-tools/schema-index.txt";

    private SchemaIndex() {
    }

    /**
     * @param text the content of an index
     * @return the paths
     */
    public static List<String> parse(String text) {
        List<String> paths = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String path = line.trim();
            if (!path.isEmpty() && !path.startsWith("#")) {
                paths.add(path.replaceFirst("^/+", ""));
            }
        }
        return paths;
    }

    /**
     * @param paths the classpath paths of the schema files
     * @return the content of an index, sorted
     */
    public static String render(List<String> paths) {
        StringBuilder text = new StringBuilder("# GraphQL schema files, one classpath path per line\n");
        paths.stream().sorted().forEach(path -> text.append(path).append('\n'));
        return text.toString();
    }
}
