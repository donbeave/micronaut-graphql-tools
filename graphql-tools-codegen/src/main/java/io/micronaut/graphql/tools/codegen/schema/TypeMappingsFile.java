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

import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.GeneratedFile;
import io.micronaut.graphql.tools.codegen.ir.AbstractType;
import io.micronaut.graphql.tools.codegen.ir.EnumType;
import io.micronaut.graphql.tools.codegen.ir.InputType;
import io.micronaut.graphql.tools.codegen.ir.Ir;
import io.micronaut.graphql.tools.codegen.ir.ModelType;
import io.micronaut.graphql.tools.codegen.ir.ScalarType;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The file {@value #PATH}: it maps the SDL name of each type that a module generates, and of each custom scalar that it maps, to
 * the qualified name of its class. The generator writes it for its own output, and reads it from the dependencies of a module so
 * that a schema that refers to a type of another module needs no {@code typeMappings} entry. The content is sorted and has no
 * date, so the same IR always gives the same bytes.
 */
public final class TypeMappingsFile {

    /** The path of the file, relative to a resource root or to the root of a jar. */
    public static final String PATH = "META-INF/micronaut-graphql-tools/type-mappings.properties";

    private static final Pattern CLASS_NAME = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*(\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*");

    private static final String OPTION = "typeMappingsFromDependencies";

    private TypeMappingsFile() {
    }

    /**
     * The file for the output of a module.
     *
     * @param ir the IR of the module
     * @return the file, or {@code null} when the module generates and maps no type
     */
    public static GeneratedFile of(Ir ir) {
        Map<String, String> mappings = new TreeMap<>();
        for (EnumType type : ir.enums()) {
            put(mappings, type.names());
        }
        for (InputType type : ir.inputs()) {
            put(mappings, type.names());
        }
        // the models of a client are the results of its operations, not the class of a GraphQL type
        if (ir.operations().isEmpty()) {
            for (ModelType type : ir.models()) {
                put(mappings, type.names());
            }
            for (AbstractType type : ir.abstractTypes()) {
                put(mappings, type.names());
            }
        }
        for (ScalarType type : ir.scalars()) {
            mappings.put(type.names().sdlName(), type.className());
        }
        if (mappings.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder("# The GraphQL types of this module and the classes they map to.\n");
        mappings.forEach((name, className) -> text.append(name).append('=').append(escape(className)).append('\n'));
        return new GeneratedFile(PATH, text.toString());
    }

    private static void put(Map<String, String> mappings, Names names) {
        String qualified = names.packageName().isEmpty() ? names.simpleName() : names.packageName() + "." + names.simpleName();
        mappings.put(names.sdlName(), qualified);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Reads the file from jars and directories. The entries are read in the order of their paths, and the first mapping of a name
     * wins; a different class for the same name is a warning.
     *
     * @param dependencies the jars and class directories, in any order; one that does not exist or has no file is skipped
     * @param diagnostics  receives the warnings
     * @return the mappings, in the order of the names
     */
    static Map<String, String> read(Collection<Path> dependencies, List<Diagnostic> diagnostics) {
        Map<String, String> mappings = new TreeMap<>();
        Map<String, String> sources = new TreeMap<>();
        for (Path dependency : new TreeSet<>(dependencies)) {
            Properties properties = load(dependency, diagnostics);
            for (String name : new TreeSet<>(properties.stringPropertyNames())) {
                String className = properties.getProperty(name).trim();
                if (!CLASS_NAME.matcher(className).matches()) {
                    warn(diagnostics, "The file " + PATH + " of " + dependency + " maps '" + name + "' to '" + className
                            + "', which is not a qualified class name, so the mapping is ignored.");
                } else if (!mappings.containsKey(name)) {
                    mappings.put(name, className);
                    sources.put(name, dependency.toString());
                } else if (!mappings.get(name).equals(className)) {
                    warn(diagnostics, "The dependencies " + sources.get(name) + " and " + dependency + " map '" + name + "' to "
                            + mappings.get(name) + " and " + className + ". The first is used; set typeMappings." + name
                            + " to choose.");
                }
            }
        }
        return mappings;
    }

    private static Properties load(Path dependency, List<Diagnostic> diagnostics) {
        Properties properties = new Properties();
        try {
            if (Files.isDirectory(dependency)) {
                Path file = dependency.resolve(PATH);
                if (Files.isRegularFile(file)) {
                    try (InputStream in = Files.newInputStream(file)) {
                        properties.load(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
                    }
                }
            } else if (Files.isRegularFile(dependency)) {
                try (ZipFile zip = new ZipFile(dependency.toFile())) {
                    ZipEntry entry = zip.getEntry(PATH);
                    if (entry != null) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            properties.load(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            warn(diagnostics, "The dependency " + dependency + " cannot be read for " + PATH + ": " + e.getMessage());
            return new Properties();
        }
        return properties;
    }

    private static void warn(List<Diagnostic> diagnostics, String message) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, Diagnostic.DEPENDENCY_TYPE_MAPPINGS, message, OPTION, null));
    }
}
