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
package io.micronaut.graphql.tools.binding.internal.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The client directories of a compilation. Each subdirectory of a client directory is one client schema: its
 * {@code *.graphqls} files are the schema and its {@code *.graphql} files hold the operations and fragments. The reserved
 * name {@value #SERVER} is the schema of the server directories, the ones that hold the resolver schema, so that a client of
 * the application's own schema needs no copy of it; its operations are the {@code *.graphql} files of a subdirectory
 * {@value #SERVER} of a client directory, when there is one.
 *
 * <p>The directory listing is read once, on the first question that needs it.</p>
 */
@Internal
public final class ClientDirectories {

    /** The reserved schema name that reads the server schema directories. */
    public static final String SERVER = "server";

    private final List<Path> clientDirs;
    private final List<Path> serverDirs;
    private final @Nullable String foreign;
    private List<String> names;

    /**
     * @param clientDirs the client directories, as the build passes them
     * @param serverDirs the server schema directories, the schema of the name {@value #SERVER}
     */
    public ClientDirectories(List<Path> clientDirs, List<Path> serverDirs) {
        this(clientDirs, serverDirs, null);
    }

    /**
     * @param clientDirs the client directories, as the build passes them
     * @param serverDirs the server schema directories, the schema of the name {@value #SERVER}
     * @param foreign    why the directories are not those of this compilation (they were set for another module of the same
     *                   JVM), or null when they are; every question that needs a directory then fails with this reason
     */
    public ClientDirectories(List<Path> clientDirs, List<Path> serverDirs, @Nullable String foreign) {
        this.clientDirs = List.copyOf(clientDirs);
        this.serverDirs = List.copyOf(serverDirs);
        this.foreign = foreign;
    }

    /**
     * @param requested the {@code schema} member of a client, empty for the only schema of the client directories
     * @param member    where a problem is reported
     * @return the layout of the schema, or a {@code client-schema-missing} finding
     */
    public Resolved resolve(String requested, String member) {
        if (foreign != null) {
            return new Resolved(null, missing(member, foreign, "set micronaut.graphql.tools.processor.client.dirs, "
                    + "micronaut.graphql.tools.processor.schema.dirs and micronaut.graphql.tools.processor.module.dir in this "
                    + "module, or compile without client checks with micronaut.graphql.tools.processor.client.checks=false"));
        }
        String name = requested;
        if (requested.isEmpty()) {
            Finding problem = checkDirectories(member);
            if (problem != null) {
                return new Resolved(null, problem);
            }
            List<String> available = names();
            if (available.size() != 1) {
                return new Resolved(null, missing(member, available.isEmpty()
                        ? "the client directories " + clientDirs + " have no schema directory"
                        : "the client directories " + clientDirs + " have several schemas " + available + " and the client "
                        + "names none", available.isEmpty() ? hintNone() : "set the schema member of the client to one of "
                        + available));
            }
            name = available.get(0);
        }
        if (name.equals(SERVER)) {
            if (serverDirs.isEmpty()) {
                return new Resolved(null, missing(member, "the schema " + SERVER + " is the schema of the schema directories, "
                        + "and none is configured", "set micronaut.graphql.tools.processor.schema.dirs"));
            }
            return new Resolved(new Layout(SERVER, serverDirs, subdirectories(SERVER)), null);
        }
        Finding problem = checkDirectories(member);
        if (problem != null) {
            return new Resolved(null, problem);
        }
        List<Path> directories = subdirectories(name);
        if (directories.isEmpty()) {
            return new Resolved(null, missing(member, "the client directories " + clientDirs + " have no schema directory named "
                    + name, names().isEmpty() ? hintNone() : "add the directory " + name + " to a client directory, or set the "
                    + "schema member of the client to one of " + names()));
        }
        return new Resolved(new Layout(name, directories, directories), null);
    }

    private String hintNone() {
        return "set micronaut.graphql.tools.processor.client.dirs to a directory with one subdirectory per client schema, or set "
                + "the schema member of the client to \"" + SERVER + "\" to check against micronaut.graphql.tools.processor.schema.dirs";
    }

    private @Nullable Finding checkDirectories(String member) {
        if (clientDirs.isEmpty()) {
            return missing(member, "the client directories are not configured", hintNone());
        }
        for (Path directory : clientDirs) {
            if (!Files.isDirectory(directory)) {
                return missing(member, "the client directory " + directory.toAbsolutePath() + " does not exist or is not a "
                        + "directory", "correct micronaut.graphql.tools.processor.client.dirs");
            }
        }
        return null;
    }

    private static Finding missing(String member, String sentence, String fix) {
        return new Finding(BindingCode.CLIENT_SCHEMA_MISSING.getCode(), member, sentence, null, null, null, fix);
    }

    // the names of the schemas that the client directories define, not the reserved name
    private List<String> names() {
        if (names == null) {
            TreeSet<String> found = new TreeSet<>();
            for (Path directory : clientDirs) {
                for (Path child : children(directory)) {
                    String name = child.getFileName().toString();
                    if (!name.equals(SERVER)) {
                        found.add(name);
                    }
                }
            }
            names = List.copyOf(found);
        }
        return names;
    }

    private List<Path> subdirectories(String name) {
        List<Path> found = new ArrayList<>();
        for (Path directory : clientDirs) {
            Path child = directory.resolve(name);
            if (Files.isDirectory(child)) {
                found.add(child);
            }
        }
        return found;
    }

    private static List<Path> children(Path directory) {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isDirectory).sorted().toList();
        } catch (IOException | UncheckedIOException e) {
            return List.of();
        }
    }

    /**
     * The directories of one schema.
     *
     * @param name          the schema name
     * @param schemaDirs    the directories of the {@code *.graphqls} files
     * @param documentDirs  the directories of the {@code *.graphql} files
     */
    public record Layout(String name, List<Path> schemaDirs, List<Path> documentDirs) {

        public Layout {
            schemaDirs = List.copyOf(schemaDirs);
            documentDirs = List.copyOf(documentDirs);
        }
    }

    /**
     * What a schema name resolves to.
     *
     * @param layout  the directories, null when the name does not resolve
     * @param problem why it does not, null when it does
     */
    public record Resolved(@Nullable Layout layout, @Nullable Finding problem) {
    }
}
