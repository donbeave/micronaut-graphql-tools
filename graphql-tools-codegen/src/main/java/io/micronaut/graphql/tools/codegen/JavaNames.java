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
package io.micronaut.graphql.tools.codegen;

import java.util.Set;

/**
 * The syntax of Java names, for the options that hold a package or a class name.
 */
final class JavaNames {

    private static final Set<String> RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default",
            "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "_", "true", "false", "null");

    private JavaNames() {
    }

    static boolean isReserved(String text) {
        return RESERVED.contains(text);
    }

    static boolean isIdentifier(String text) {
        if (text.isEmpty() || RESERVED.contains(text) || !Character.isJavaIdentifierStart(text.codePointAt(0))) {
            return false;
        }
        return text.codePoints().allMatch(Character::isJavaIdentifierPart);
    }

    static boolean isQualifiedName(String text) {
        for (String part : text.split("\\.", -1)) {
            if (!isIdentifier(part)) {
                return false;
            }
        }
        return true;
    }
}
