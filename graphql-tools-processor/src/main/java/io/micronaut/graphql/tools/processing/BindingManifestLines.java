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
package io.micronaut.graphql.tools.processing;

import graphql.GraphQL;
import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

final class BindingManifestLines {

    static final String PATH = "META-INF/micronaut-graphql-tools/binding-manifest.txt";

    static final String OMITTED_PATH = "META-INF/micronaut-graphql-tools/binding-manifest-omitted.txt";

    static final String ACCESSORS_PATH = "META-INF/micronaut-graphql-tools/accessors.txt";

    static final String ACCESSORS_FORMAT = "# graphql-accessors 1";

    static final String OMITTED_FORMAT = "# graphql-binding-manifest-omitted 1";

    static final String FORMAT = "# graphql-binding-manifest 1";

    private static final String ARROW = " → ";
    private static final String DOT = " · ";
    private static final String NONE = "-";

    private final Map<String, String> lines = new HashMap<>();
    private final Set<String> diagnostics = new TreeSet<>();
    private final Set<String> invalidParts = new TreeSet<>();

    void type(String typeName, String kind, String javaType) {
        lines.put(typeName, line(typeName, kind, javaType == null ? NONE : javaType, NONE, NONE));
    }

    void field(ObjectTypeDefinition type, FieldDefinition field, String kind, String member, String declaredType,
               String convertedType) {
        StringBuilder coordinate = new StringBuilder(type.getName()).append('.').append(field.getName());
        if (!field.getInputValueDefinitions().isEmpty()) {
            coordinate.append(field.getInputValueDefinitions().stream()
                    .map(it -> it.getName() + ": " + BindingChecks.printType(it.getType()))
                    .collect(Collectors.joining(", ", "(", ")")));
        }
        coordinate.append(": ").append(BindingChecks.printType(field.getType()));
        lines.put(type.getName() + "." + field.getName(),
                line(coordinate.toString(), kind, member, declaredType, convertedType));
    }

    void enumValue(String enumTypeName, String value, String enumClass) {
        lines.put(enumTypeName + "." + value,
                line(enumTypeName + "." + value, "enumValue", enumClass + "#" + value, NONE, NONE));
    }

    void inputField(String inputTypeName, String fieldName, Type<?> graphQlType, String inputClass, String property,
                    String declaredType, String convertedType) {
        lines.put(inputTypeName + "." + fieldName, line(inputTypeName + "." + fieldName + ": "
                + BindingChecks.printType(graphQlType), "inputField", inputClass + "#" + property, declaredType,
                convertedType));
    }

    void diagnostic(String kind, String... parts) {
        diagnostics.add("!" + kind + " " + Arrays.stream(parts).map(this::requirePart).collect(Collectors.joining(DOT)));
    }

    boolean hasType(String typeName) {
        return lines.containsKey(typeName);
    }

    Set<String> invalidParts() {
        return invalidParts;
    }

    List<String> body() {
        List<String> body = new ArrayList<>(lines.values());
        body.addAll(diagnostics);
        body.sort(Comparator.naturalOrder());
        return body;
    }

    String render(TypeDefinitionRegistry registry) {
        StringBuilder builder = new StringBuilder();
        List.of(FORMAT,
                "# binder: micronaut-graphql-tools " + version(BindingManifestLines.class.getPackage()
                        .getImplementationVersion()),
                "# graphql-java: " + graphQlJavaVersion(),
                "# schema-sha256: " + schemaSha256(registry)
        ).forEach(it -> builder.append(it).append('\n'));
        body().forEach(it -> builder.append(it).append('\n'));
        return builder.toString();
    }

    private String line(String subject, String kind, String member, String declaredType, String convertedType) {
        return requirePart(subject) + ARROW + kind + DOT + requirePart(member) + DOT + requirePart(declaredType) + DOT
                + requirePart(convertedType);
    }

    private String requirePart(String part) {
        if (part.contains(ARROW) || part.contains(DOT) || part.contains("\n") || part.contains("\r")) {
            invalidParts.add(part);
        }
        return part;
    }

    private static String version(String version) {
        return version == null || version.isBlank() ? "unknown" : version;
    }

    private static String graphQlJavaVersion() {
        try {
            ClassLoader classLoader = GraphQL.class.getClassLoader();
            Enumeration<URL> resources = classLoader.getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                try (InputStream inputStream = resources.nextElement().openStream()) {
                    Manifest manifest = new Manifest(inputStream);
                    if ("graphql-java".equals(manifest.getMainAttributes().getValue("Bundle-SymbolicName"))) {
                        String version = manifest.getMainAttributes().getValue("Bundle-Version");
                        return version(version == null ? null : version.replaceFirst("^(\\d+\\.\\d+)\\.0$", "$1"));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
        }
        return "unknown";
    }

    static String schemaSha256(TypeDefinitionRegistry registry) {
        List<Definition<?>> definitions = new ArrayList<>();
        registry.schemaDefinition().ifPresent(definitions::add);
        definitions.addAll(registry.getSchemaExtensionDefinitions());

        Map<String, List<Definition<?>>> named = new TreeMap<>();
        registry.types().values().forEach(it -> add(named, it.getName(), it));
        registry.scalars().values().stream()
                .filter(it -> !ScalarInfo.isGraphqlSpecifiedScalar(it.getName()))
                .forEach(it -> add(named, it.getName(), it));
        registry.getDirectiveDefinitions().values().stream()
                .filter(it -> !GraphQLSpecifiedDirectives.isProvided(it.getName()))
                .forEach(it -> add(named, it.getName(), it));
        addExtensions(named, registry.objectTypeExtensions());
        addExtensions(named, registry.interfaceTypeExtensions());
        addExtensions(named, registry.unionTypeExtensions());
        addExtensions(named, registry.enumTypeExtensions());
        addExtensions(named, registry.scalarTypeExtensions());
        addExtensions(named, registry.inputObjectTypeExtensions());
        named.values().forEach(definitions::addAll);

        String printed = definitions.stream().map(AstPrinter::printAst).collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(printed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void addExtensions(Map<String, List<Definition<?>>> named,
                                      Map<String, ? extends List<? extends Definition<?>>> extensions) {
        extensions.forEach((name, list) -> list.forEach(it -> add(named, name, it)));
    }

    private static void add(Map<String, List<Definition<?>>> named, String name, Definition<?> node) {
        named.computeIfAbsent(name, it -> new ArrayList<>()).add(node);
    }

}
