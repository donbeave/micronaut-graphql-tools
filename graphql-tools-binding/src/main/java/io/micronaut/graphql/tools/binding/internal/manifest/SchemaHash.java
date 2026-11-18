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
package io.micronaut.graphql.tools.binding.internal.manifest;

import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The {@code # schema-sha256:} line of the binding manifest: a SHA-256 over the definitions of a schema, printed by
 * graphql-java's {@code AstPrinter}, in an order that does not depend on how the schema was loaded. A definition sorts
 * by its name, then the definition before its extensions, then by its printed text. The order of the files, of the
 * definitions in a file and of the extensions of one type therefore does not change the hash; the order of the fields
 * inside one definition does. Built-in scalars and the directives the active graphql-java adds itself are left out.
 */
@Internal
public final class SchemaHash {

    private SchemaHash() {
    }

    /**
     * @param registry          the merged definitions of the schema
     * @param providedDirective true for the name of a directive that graphql-java defines on its own
     * @return the SHA-256 as 64 lower case hex digits
     */
    public static String sha256(TypeDefinitionRegistry registry, Predicate<String> providedDirective) {
        List<Part> parts = new ArrayList<>();
        registry.schemaDefinition().ifPresent(it -> parts.add(new Part("", 0, it)));
        registry.getSchemaExtensionDefinitions().forEach(it -> parts.add(new Part("", 1, it)));
        registry.types().values().forEach(it -> parts.add(new Part(it.getName(), 0, it)));
        registry.scalars().values().stream()
                .filter(it -> !ScalarInfo.isGraphqlSpecifiedScalar(it.getName()))
                .forEach(it -> parts.add(new Part(it.getName(), 0, it)));
        registry.getDirectiveDefinitions().values().stream()
                .filter(it -> !providedDirective.test(it.getName()))
                .forEach(it -> parts.add(new Part(it.getName(), 0, it)));
        extensions(parts, registry.objectTypeExtensions());
        extensions(parts, registry.interfaceTypeExtensions());
        extensions(parts, registry.unionTypeExtensions());
        extensions(parts, registry.enumTypeExtensions());
        extensions(parts, registry.scalarTypeExtensions());
        extensions(parts, registry.inputObjectTypeExtensions());

        parts.sort(Comparator.comparing(Part::name).thenComparingInt(Part::rank).thenComparing(Part::text));
        String printed = parts.stream().map(Part::text).collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(printed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void extensions(List<Part> parts, Map<String, ? extends List<? extends Definition<?>>> extensions) {
        extensions.forEach((name, list) -> list.forEach(it -> parts.add(new Part(name, 1, it))));
    }

    private record Part(String name, int rank, String text) {

        Part(String name, int rank, Definition<?> definition) {
            this(name, rank, AstPrinter.printAst(definition));
        }
    }
}
