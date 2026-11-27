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

import java.util.List;
import java.util.regex.Pattern;

/**
 * The globs of the {@code includes} and {@code excludes} options. They match a relative path with {@code /} separators on every
 * platform: {@code *} is any characters but {@code /}, {@code ?} is one such character, {@code **} is any characters including
 * {@code /}, {@code **}{@code /} is any number of directories (also none, so {@code **}{@code /*.graphqls} matches a file in the root),
 * and {@code {a,b}} is an alternative. Every other character stands for itself.
 */
final class Globs {

    private Globs() {
    }

    static boolean matchesAny(List<Pattern> patterns, String path) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    static List<Pattern> compile(List<String> globs) {
        return globs.stream().map(Globs::compile).toList();
    }

    static Pattern compile(String glob) {
        boolean alternatives = balanced(glob);
        StringBuilder regex = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                i += 2;
                if (i < glob.length() && glob.charAt(i) == '/') {
                    regex.append("(?:.*/)?");
                    i++;
                } else {
                    regex.append(".*");
                }
                continue;
            }
            if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (alternatives && c == '{') {
                depth++;
                regex.append("(?:");
            } else if (alternatives && c == '}' && depth > 0) {
                depth--;
                regex.append(')');
            } else if (alternatives && c == ',' && depth > 0) {
                regex.append('|');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
            i++;
        }
        return Pattern.compile(regex.toString());
    }

    private static boolean balanced(String glob) {
        int depth = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }
}
