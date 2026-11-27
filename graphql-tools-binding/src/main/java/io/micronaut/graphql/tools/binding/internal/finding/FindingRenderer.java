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
package io.micronaut.graphql.tools.binding.internal.finding;

import io.micronaut.core.annotation.Internal;

/**
 * The one place that renders a {@link Finding}, so that a compile-time error and a startup failure use the same words:
 *
 * <pre>
 * [code] coordinate: sentence.
 *   at binary.Name#member(parameter types) (declared in binary.Contract)
 *   schema /absolute/path.graphqls:3:5
 *   document /absolute/path.graphql:3:5
 *   fix: the change that resolves it
 * </pre>
 *
 * The first line always exists, with {@code -} for a finding without a coordinate. Each other line exists only when the
 * finding carries the part.
 */
@Internal
public final class FindingRenderer {

    private FindingRenderer() {
    }

    /**
     * @param finding a finding
     * @return its text, lines separated by {@code \n}, without a trailing line break
     */
    public static String render(Finding finding) {
        StringBuilder text = new StringBuilder();
        text.append('[').append(finding.code()).append("] ")
                .append(finding.coordinate() == null ? "-" : finding.coordinate()).append(": ")
                .append(sentence(finding.sentence()));
        if (finding.member() != null) {
            text.append("\n  at ").append(finding.member());
            if (finding.declaredIn() != null) {
                text.append(" (declared in ").append(finding.declaredIn()).append(')');
            }
        }
        if (finding.schemaLocation() != null) {
            text.append("\n  schema ").append(finding.schemaLocation());
        }
        if (finding.documentLocation() != null) {
            text.append("\n  document ").append(finding.documentLocation());
        }
        if (finding.fix() != null) {
            text.append("\n  fix: ").append(finding.fix());
        }
        return text.toString();
    }

    private static String sentence(String sentence) {
        String trimmed = sentence.strip();
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return trimmed;
        }
        return trimmed + ".";
    }
}
