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
package io.micronaut.graphql.tools.schema;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaIndex;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaLocation;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaLocation.Scheme;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSourceException;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources.Source;
import io.micronaut.graphql.tools.exceptions.SchemaDiscoveryException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds the schema files of the configured locations at startup. A directory of the classpath is listed on the JVM and read
 * from the schema index ({@value SchemaIndex#RESOURCE}) in a native image, which cannot list one. Nothing here uses reflection.
 */
@Internal
public final class SchemaDiscovery {

    private SchemaDiscovery() {
    }

    /**
     * Reads and merges the schema files of the locations.
     *
     * @param locations the locations, see {@link SchemaLocation}
     * @param loader    the class loader that holds the classpath locations
     * @return the schema and the names of the files read and skipped
     * @throws SchemaDiscoveryException when a location is invalid, names a file that is missing, matches no file at all, or a
     *                                  file does not parse
     */
    public static SchemaSources.Loaded discover(List<String> locations, ClassLoader loader) {
        return discover(locations, loader, System.getProperty("org.graalvm.nativeimage.imagecode") != null);
    }

    /**
     * @param locations the locations
     * @param loader    the class loader that holds the classpath locations
     * @param useIndex  whether a wildcard classpath location is resolved from the index instead of listing directories
     * @return the schema and the names of the files read and skipped
     */
    public static SchemaSources.Loaded discover(List<String> locations, ClassLoader loader, boolean useIndex) {
        List<Source> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String text : locations) {
            SchemaLocation location;
            try {
                location = SchemaLocation.parse(text);
            } catch (IllegalArgumentException e) {
                throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "Invalid schema location in micronaut.graphql.tools.schema.locations: "
                        + e.getMessage(), e);
            }
            try {
                for (Source source : find(location, loader, useIndex, seen)) {
                    sources.add(source);
                }
            } catch (IOException | UncheckedIOException | InvalidPathException e) {
                throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "Cannot read the schema location " + location + ": " + e.getMessage(), e);
            }
        }
        if (sources.isEmpty()) {
            throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "No GraphQL schema files found for micronaut.graphql.tools.schema.locations="
                    + locations + ". Put the schema in a " + SchemaSources.EXTENSION + " file under the first location, set the "
                    + "locations, or provide a TypeDefinitionRegistry bean.");
        }
        SchemaSources.Loaded loaded;
        try {
            loaded = SchemaSources.load(sources);
        } catch (SchemaSourceException e) {
            throw new SchemaDiscoveryException(BindingCode.SCHEMA_INVALID, "Cannot load the GraphQL schema: " + e.getMessage(), e);
        }
        if (loaded.read().isEmpty()) {
            throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "No GraphQL schema found for micronaut.graphql.tools.schema.locations="
                    + locations + ": every file that matched is empty or holds only operations, skipped: " + loaded.skipped() + ".");
        }
        return loaded;
    }

    private static List<Source> find(SchemaLocation location, ClassLoader loader, boolean useIndex, Set<String> seen)
            throws IOException {
        List<Source> found = new ArrayList<>();
        switch (location.scheme()) {
            case FILE -> findFiles(location, found, seen);
            case CLASSPATH, CLASSPATH_ALL -> {
                if (!location.isPattern()) {
                    findLiteral(location, loader, found, seen);
                } else if (useIndex) {
                    findInIndex(location, loader, found, seen);
                } else {
                    findByListing(location, loader, found, seen);
                }
            }
            default -> throw new IllegalStateException(String.valueOf(location.scheme()));
        }
        return found;
    }

    private static void findFiles(SchemaLocation location, List<Source> found, Set<String> seen) throws IOException {
        if (!location.isPattern()) {
            Path file = Path.of(location.pattern());
            if (!Files.isRegularFile(file)) {
                throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "The schema file " + location + " does not exist.");
            }
            if (seen.add(key(file))) {
                found.add(new Source(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)));
            }
            return;
        }
        Path root = Path.of(location.root());
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(it -> location.matches(it.toString().replace('\\', '/')))
                    .sorted()
                    .collect(Collectors.toList());
        }
        for (Path file : files) {
            if (seen.add(key(file))) {
                found.add(new Source(root.relativize(file).toString().replace('\\', '/'), Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
    }

    private static String key(Path file) throws IOException {
        return file.toAbsolutePath().normalize().toUri().toURL().toExternalForm();
    }

    private static void findLiteral(SchemaLocation location, ClassLoader loader, List<Source> found, Set<String> seen)
            throws IOException {
        List<URL> urls = Collections.list(loader.getResources(location.pattern()));
        if (urls.isEmpty()) {
            throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "The schema file " + location + " is not on the classpath.");
        }
        for (URL url : location.scheme() == Scheme.CLASSPATH ? urls.subList(0, 1) : urls) {
            add(location.pattern(), url, found, seen);
        }
    }

    private static void findInIndex(SchemaLocation location, ClassLoader loader, List<Source> found, Set<String> seen)
            throws IOException {
        List<URL> indexes = Collections.list(loader.getResources(SchemaIndex.RESOURCE));
        if (indexes.isEmpty()) {
            throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "The wildcard schema location " + location + " cannot list the classpath in a "
                    + "native image, and no " + SchemaIndex.RESOURCE + " is on it. Generate the index of the schema files "
                    + "at build time, or name each file in micronaut.graphql.tools.schema.locations.");
        }
        for (URL index : indexes) {
            Set<String> names = new TreeSet<>();
            for (String path : SchemaIndex.parse(read(index))) {
                if (location.matches(path)) {
                    names.add(path);
                }
            }
            for (String name : names) {
                List<URL> urls = Collections.list(loader.getResources(name));
                if (urls.isEmpty()) {
                    throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "The schema index " + index + " lists " + name
                            + ", which is not on the classpath. Regenerate the index from the files that are packaged.");
                }
                for (URL url : location.scheme() == Scheme.CLASSPATH ? urls.subList(0, Math.min(1, urls.size())) : urls) {
                    add(name, url, found, seen);
                }
            }
            if (location.scheme() == Scheme.CLASSPATH && !names.isEmpty()) {
                return;
            }
        }
    }

    private static void findByListing(SchemaLocation location, ClassLoader loader, List<Source> found, Set<String> seen)
            throws IOException {
        List<URL> roots = Collections.list(loader.getResources(location.root()));
        for (URL root : location.scheme() == Scheme.CLASSPATH && !roots.isEmpty() ? roots.subList(0, 1) : roots) {
            List<Source> inRoot = new ArrayList<>();
            switch (root.getProtocol()) {
                case "file" -> listDirectory(location, root, inRoot, seen);
                case "jar" -> listJar(location, root, inRoot, seen);
                default -> throw new SchemaDiscoveryException(BindingCode.SCHEMA_NOT_FOUND, "Cannot list " + root + " for the schema location " + location
                        + ": only directories and jar files can be listed. Name each file in "
                        + "micronaut.graphql.tools.schema.locations.");
            }
            inRoot.sort(Comparator.comparing(Source::name));
            found.addAll(inRoot);
        }
        if ((roots.isEmpty() || location.scheme() == Scheme.CLASSPATH_ALL) && loader.getResource(SchemaIndex.RESOURCE) != null) {
            // an entry that cannot be listed (a jar without directory entries) is found through its index; seen drops the files that were listed
            findInIndex(location, loader, found, seen);
        }
    }

    private static void listDirectory(SchemaLocation location, URL root, List<Source> found, Set<String> seen)
            throws IOException {
        Path directory;
        try {
            directory = Path.of(root.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        String prefix = location.root();
        List<Path> files;
        try (Stream<Path> paths = Files.walk(directory)) {
            files = paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        for (Path file : files) {
            String name = prefix + directory.relativize(file).toString().replace('\\', '/');
            if (location.matches(name) && seen.add(key(file))) {
                found.add(new Source(name, Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
    }

    private static void listJar(SchemaLocation location, URL root, List<Source> found, Set<String> seen)
            throws IOException {
        URLConnection connection = root.openConnection();
        if (!(connection instanceof JarURLConnection jarConnection)) {
            throw new IOException("not a jar URL: " + root);
        }
        jarConnection.setUseCaches(false);
        try (JarFile jar = jarConnection.getJarFile()) {
            String jarName = new File(jar.getName()).getName();
            String base = root.toExternalForm();
            String jarBase = base.substring(0, base.indexOf("!/") + 2);
            List<JarEntry> entries = Collections.list(jar.entries());
            for (JarEntry entry : entries) {
                if (!entry.isDirectory() && location.matches(entry.getName())
                        && seen.add(jarBase + entry.getName())) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        found.add(new Source(jarName + "!/" + entry.getName(),
                                new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
        }
    }

    private static void add(String name, URL url, List<Source> found, Set<String> seen) throws IOException {
        if (seen.add(url.toExternalForm())) {
            found.add(new Source(qualified(name, url), read(url)));
        }
    }

    private static String qualified(String name, URL url) {
        String text = url.toExternalForm();
        int bang = text.indexOf("!/");
        return "jar".equals(url.getProtocol()) && bang > 0 ? text.substring(text.lastIndexOf('/', bang) + 1, bang) + "!/" + name : name;
    }

    private static String read(URL url) throws IOException {
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
