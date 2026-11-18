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
package io.micronaut.graphql.tools;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.graphql.tools.binding.internal.manifest.ManifestRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the schema is bound to: one line per schema coordinate, saying which Java member serves it, in the text format
 * {@code graphql-binding-manifest 1}. Available as a bean once the {@code GraphQL} bean is built, and written at
 * startup with {@code micronaut.graphql.tools.manifest.enabled}.
 *
 * <p>The header names the format, the binder, the graphql-java version and a SHA-256 of the schema definitions. The
 * body is sorted by {@link String#compareTo(String)}:</p>
 * <ul>
 *     <li>{@code Type → object|interface|union|enum|input|scalar · <Java type> · - · -}</li>
 *     <li>{@code Type.field(arg: T): T → root|resolver|property · <member> · <declared type> · <converted type>}, where
 *     the member is {@code Class#method(parameter types)} and the converted type is the declared type without
 *     {@code CompletionStage}, {@code Publisher} (subscription fields), {@code DataFetcherResult} (also per list
 *     element) and {@code Optional}, unwrapped once in that order; the primitive optionals
 *     ({@code OptionalInt}, {@code OptionalLong}, {@code OptionalDouble}) are unwrapped as well</li>
 *     <li>{@code Abstract/Member → resolves · <Java type> · - · -}</li>
 *     <li>{@code Enum.VALUE → enumValue · <Java enum>#VALUE · - · -}</li>
 *     <li>{@code Input.field: T → inputField · <Java type>#property · <declared type> · <converted type>}</li>
 *     <li>diagnostics, starting with {@code !}: {@code !unreachable <Type>}, {@code !ambiguous <coordinate> ·
 *     <member> | <member>} and the strict-mode findings</li>
 *     <li>{@code ~ignored <member>}: a member marked {@code @GraphQLIgnore} that the binding met, which binds no
 *     field and is not reported</li>
 * </ul>
 * <p>The manifest is rendered when it is first read, not while the schema is bound, and without reflection: it is the
 * same text on the JVM and in a native image. Java types are rendered as {@link java.lang.reflect.Type#getTypeName()}
 * renders them from Micronaut's compile-time type information, which keeps no wildcards, raw types or generic array
 * components ({@code List<? extends Book>} reads {@code java.util.List<Book>}). The member of a property is the getter
 * that the compile-time checks (the processor) recorded in {@code META-INF/micronaut-graphql-tools/accessors.txt};
 * without the processor it reads {@code Type#property}. Text is UTF-8 with
 * {@code LF} line ends; nothing is escaped (a part that would contain a separator fails the build of the manifest).</p>
 */
public final class BindingManifest {

    public static final String FORMAT = ManifestRenderer.FORMAT;

    private Supplier<Content> renderer;
    private volatile Content content;

    BindingManifest(Supplier<Content> renderer) {
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
                    result = renderer.get();
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
