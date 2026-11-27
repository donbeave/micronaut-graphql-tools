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

import io.micronaut.graphql.tools.codegen.Language;

import java.util.Set;

/**
 * The words that a schema name cannot be in the generated code. Java words are reserved in every language, because the
 * generated types are read through Java introspection; the words of Kotlin and Groovy are added for those languages. The Java
 * list is the one that the options use for a package or class name, and a test keeps the two equal.
 */
final class ReservedNames {

    private static final Set<String> JAVA = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default",
            "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "_", "true", "false", "null");

    private static final Set<String> KOTLIN = Set.of(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
            "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when",
            "while");

    private static final Set<String> GROOVY = Set.of("as", "def", "in", "trait");

    private ReservedNames() {
    }

    static boolean isJava(String name) {
        return JAVA.contains(name);
    }

    /**
     * The language whose keyword the name is.
     *
     * @param language the output language
     * @param name     the schema name
     * @return {@code Java}, {@code Kotlin} or {@code Groovy}, or {@code null} when the name is not reserved
     */
    static String languageOf(Language language, String name) {
        if (JAVA.contains(name)) {
            return "Java";
        } else if (language == Language.KOTLIN && KOTLIN.contains(name)) {
            return "Kotlin";
        } else if (language == Language.GROOVY && GROOVY.contains(name)) {
            return "Groovy";
        }
        return null;
    }
}
