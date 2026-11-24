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
package io.micronaut.graphql.tools.manifest;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the schema is bound to: one line per schema coordinate, saying which Java member serves it, in the text format
 * {@code graphql-binding-manifest 2}. The format is specified, with its grammar, sorting and escape rules, in the guide
 * page "Binding Manifest". Available as a bean once the {@code GraphQL} bean is built when
 * {@code micronaut.graphql.tools.manifest.enabled} is set. It is also written at startup when
 * {@code micronaut.graphql.tools.manifest.path} is set.
 *
 * <p>The manifest is rendered when it is first read, not while the schema is bound, and without reflection: it is the
 * same text on the JVM and in a native image.</p>
 */
public final class BindingManifest {

    /** The first line of the manifest, which names the format and its version. */
    public static final String FORMAT = "# graphql-binding-manifest 2";

    private Supplier<Map.Entry<List<String>, List<String>>> renderer;
    private volatile Content content;

    /**
     * @param renderer renders the header and the lines when the manifest is first read; for the library's own use
     */
    @Internal
    public BindingManifest(Supplier<Map.Entry<List<String>, List<String>>> renderer) {
        this.renderer = renderer;
    }

    boolean isRendered() {
        return content != null;
    }

    private Content content() {
        Content result = content;
        if (result == null) {
            synchronized (this) {
                result = content;
                if (result == null) {
                    Map.Entry<List<String>, List<String>> rendered = renderer.get();
                    result = new Content(rendered.getKey(), rendered.getValue());
                    content = result;
                    renderer = null;
                }
            }
        }
        return result;
    }

    @NonNull
    public List<String> getHeader() {
        return content().header();
    }

    @NonNull
    public List<String> getLines() {
        return content().lines();
    }

    @NonNull
    public List<String> getDiagnostics() {
        return content().lines().stream().filter(it -> it.startsWith("!")).toList();
    }

    @NonNull
    public String render() {
        StringBuilder builder = new StringBuilder();
        Content rendered = content();
        rendered.header().forEach(it -> builder.append(it).append('\n'));
        rendered.lines().forEach(it -> builder.append(it).append('\n'));
        return builder.toString();
    }

    /**
     * Writes {@link #render()} to a file in UTF-8, creating its parent directories.
     *
     * @param path the file
     * @throws IOException if the file cannot be written
     */
    public void write(@NonNull Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, render(), StandardCharsets.UTF_8);
    }

    record Content(List<String> header, List<String> lines) {
        Content {
            header = List.copyOf(header);
            lines = List.copyOf(lines);
        }
    }

}
