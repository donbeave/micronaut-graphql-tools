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

import io.micronaut.graphql.tools.codegen.schema.TypeMappingsFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What a run leaves in the output directory and how the next run uses it. Maven has no up-to-date engine, so the plugin keeps its own
 * state in one file of the output directory, the manifest: the fingerprint of the inputs of the run and the paths of the files it
 * wrote, relative to the output directory. The next run
 * <ul>
 *   <li>skips the generation when the fingerprint is the same and every listed file still exists;</li>
 *   <li>deletes the files that the manifest lists and the new run does not emit, so a removed schema type leaves no stale source;</li>
 *   <li>writes a file only when its bytes differ, so an unchanged file keeps its modification time.</li>
 * </ul>
 * Only the files that the manifest lists are ever deleted, and only inside the output directory.
 */
final class GeneratedSources {

    static final String MANIFEST = ".graphql-tools-generated";

    private static final String FINGERPRINT_KEY = "fingerprint=";
    private static final String FILE_KEY = "file=";

    private GeneratedSources() {
    }

    /**
     * The fingerprint of the inputs of a run: the generator version, the canonical option string, and the relative path and the
     * content of each schema file in the order given. Every part is length-prefixed, so two different inputs cannot give one byte stream.
     *
     * @param generatorVersion the version of the generator
     * @param options          the string form of the options
     * @param schemaDirectory  the schema directory that the paths of the files are relative to
     * @param schemaFiles      the schema files, in the order they are read
     * @param dependencies     the jars and class directories whose type mappings file the generator reads: each path and the bytes of
     *                         that file (or its absence) are part of the fingerprint, so a new version of a dependency regenerates
     * @return the SHA-256 of the inputs, in hexadecimal
     */
    static String fingerprint(String generatorVersion, Map<String, String> options, Path schemaDirectory, List<Path> schemaFiles,
                              List<Path> dependencies) {
        MessageDigest digest = sha256();
        update(digest, "generator");
        update(digest, generatorVersion);
        update(digest, "options");
        new TreeMap<>(options).forEach((key, value) -> {
            update(digest, key);
            update(digest, value);
        });
        update(digest, "schema");
        for (Path file : schemaFiles) {
            update(digest, schemaDirectory.relativize(file).toString().replace('\\', '/'));
            try {
                byte[] content = Files.readAllBytes(file);
                update(digest, Integer.toString(content.length));
                digest.update(content);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        update(digest, "dependencies");
        for (Path dependency : new java.util.TreeSet<>(dependencies)) {
            update(digest, dependency.toString().replace('\\', '/'));
            byte[] mappings = mappingsOf(dependency);
            update(digest, mappings == null ? "none" : Integer.toString(mappings.length));
            if (mappings != null) {
                digest.update(mappings);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] mappingsOf(Path dependency) {
        try {
            if (Files.isDirectory(dependency)) {
                Path file = dependency.resolve(TypeMappingsFile.PATH);
                return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
            }
            if (Files.isRegularFile(dependency)) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(dependency.toFile())) {
                    java.util.zip.ZipEntry entry = zip.getEntry(TypeMappingsFile.PATH);
                    if (entry != null) {
                        try (java.io.InputStream in = zip.getInputStream(entry)) {
                            return in.readAllBytes();
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return ("unreadable:" + e.getMessage()).getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Reads the manifest of the previous run.
     *
     * @param outputDirectory the output directory
     * @return the manifest, or {@code null} when there is none or it cannot be read
     */
    static Manifest readManifest(Path outputDirectory) {
        Path file = outputDirectory.resolve(MANIFEST);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String fingerprint = "";
        List<String> files = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith(FINGERPRINT_KEY)) {
                    fingerprint = line.substring(FINGERPRINT_KEY.length());
                } else if (line.startsWith(FILE_KEY)) {
                    files.add(line.substring(FILE_KEY.length()));
                }
            }
        } catch (IOException e) {
            return null;
        }
        return new Manifest(fingerprint, files);
    }

    /**
     * Whether the previous run can stand in for this one: the same fingerprint, and none of its files is gone.
     *
     * @param outputDirectory the output directory that holds the manifest
     * @param fingerprint     the fingerprint of this run
     * @return {@code true} when the generation can be skipped
     */
    static boolean isUpToDate(Path outputDirectory, String fingerprint) {
        Manifest manifest = readManifest(outputDirectory);
        return manifest != null && manifest.fingerprint().equals(fingerprint)
                && manifest.files().stream().allMatch(path -> {
                    Path file = inside(outputDirectory, path);
                    return file != null && Files.isRegularFile(file);
                });
    }

    /**
     * Writes the files, deletes the files of the previous run that are not among them, and records the manifest.
     *
     * @param outputDirectory the output directory
     * @param fingerprint     the fingerprint of this run
     * @param files           the generated files by path relative to the output directory ({@code /} separators)
     * @return what was written, left as it was and deleted
     * @throws IOException              when a file cannot be written or deleted
     * @throws IllegalArgumentException when a path leaves the output directory
     */
    static Outcome reconcile(Path outputDirectory, String fingerprint, Map<String, String> files) throws IOException {
        Path root = outputDirectory.toAbsolutePath().normalize();
        Map<String, Path> targets = new TreeMap<>();
        for (String path : files.keySet()) {
            Path target = inside(root, path);
            if (target == null) {
                throw new IllegalArgumentException("The generated file " + path + " is outside the output directory " + root);
            }
            targets.put(path, target);
        }
        int written = 0;
        int unchanged = 0;
        for (Map.Entry<String, Path> entry : targets.entrySet()) {
            byte[] bytes = files.get(entry.getKey()).getBytes(StandardCharsets.UTF_8);
            Path target = entry.getValue();
            if (Files.isRegularFile(target) && java.util.Arrays.equals(Files.readAllBytes(target), bytes)) {
                unchanged++;
            } else {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                written++;
            }
        }
        int deleted = 0;
        Manifest previous = readManifest(root);
        if (previous != null) {
            Set<String> stale = new LinkedHashSet<>(previous.files());
            stale.removeAll(targets.keySet());
            for (String path : stale) {
                Path file = inside(root, path);
                if (file != null && Files.deleteIfExists(file)) {
                    deleted++;
                    removeEmptyDirectories(root, file.getParent());
                }
            }
        }
        writeManifest(root, fingerprint, targets.keySet());
        return new Outcome(written, unchanged, deleted);
    }

    private static void writeManifest(Path root, String fingerprint, Set<String> paths) throws IOException {
        StringBuilder text = new StringBuilder("# The files that micronaut-graphql-tools-maven-plugin generated. Do not edit.\n");
        text.append(FINGERPRINT_KEY).append(fingerprint).append('\n');
        paths.forEach(path -> text.append(FILE_KEY).append(path).append('\n'));
        Files.createDirectories(root);
        Files.writeString(root.resolve(MANIFEST), text, StandardCharsets.UTF_8);
    }

    private static void removeEmptyDirectories(Path root, Path directory) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(root) && current.startsWith(root)) {
            try (var children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(current);
            current = current.getParent();
        }
    }

    /** The file at a relative path, or {@code null} when the path is empty, absolute or leaves the directory. */
    private static Path inside(Path root, String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")) {
            return null;
        }
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(path).normalize();
        return target.startsWith(base) && !target.equals(base) ? target : null;
    }

    private static void update(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * What the previous run recorded.
     *
     * @param fingerprint the fingerprint of the inputs of that run
     * @param files       the paths of the files it wrote, relative to the output directory
     */
    record Manifest(String fingerprint, List<String> files) {
    }

    /**
     * What a reconciliation did.
     *
     * @param written   the number of files written because they were new or their bytes differed
     * @param unchanged the number of files that already had the bytes
     * @param deleted   the number of files of the previous run that this run no longer emits
     */
    record Outcome(int written, int unchanged, int deleted) {
    }
}
