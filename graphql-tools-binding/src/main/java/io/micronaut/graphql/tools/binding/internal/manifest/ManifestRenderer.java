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

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.plan.BindingPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders a {@link BindingPlan} as the text format {@code graphql-binding-manifest 1}: a header and one line per entry,
 * {@code subject → kind · member · declared type · converted type}, sorted with {@link String#compareTo(String)}.
 * Nothing is escaped: a part that holds a separator or a line break fails the rendering.
 */
@Internal
public final class ManifestRenderer {

    /** The first line of a manifest. */
    public static final String FORMAT = "# graphql-binding-manifest 1";

    private static final String ARROW = " → ";
    private static final String DOT = " · ";

    private ManifestRenderer() {
    }

    /**
     * @param plan        what was bound
     * @param headerLines the header lines after {@link #FORMAT}
     * @return the manifest
     */
    public static Rendered render(BindingPlan plan, List<String> headerLines) {
        List<String> body = new ArrayList<>(plan.entries().size() + plan.diagnostics().size() + plan.ignored().size());
        for (BindingPlan.Entry entry : plan.entries()) {
            body.add(line(entry.subject(), entry.kind(), entry.member().get(), entry.declaredType().get(),
                    entry.convertedType().get()));
        }
        body.addAll(plan.diagnostics());
        body.addAll(plan.ignored());
        body.sort(Comparator.naturalOrder());

        List<String> header = new ArrayList<>(headerLines.size() + 1);
        header.add(FORMAT);
        header.addAll(headerLines);
        return new Rendered(header, body);
    }

    /**
     * @param kind  the kind of the finding, for example {@code unreachable}
     * @param parts the parts of the finding
     * @return the diagnostic line
     */
    public static String diagnostic(String kind, String... parts) {
        return "!" + kind + " " + Arrays.stream(parts)
                .map(ManifestRenderer::requirePart)
                .collect(Collectors.joining(DOT));
    }

    /**
     * @param member a member marked {@code @GraphQLIgnore} that the binding met
     * @return the line for it
     */
    public static String ignored(String member) {
        return "~ignored " + requirePart(member);
    }

    /**
     * @param subject       what the line is about
     * @param kind          how it is bound
     * @param member        the Java member or type that serves it
     * @param declaredType  the declared Java type
     * @param convertedType the converted Java type
     * @return the line
     */
    public static String line(String subject, String kind, String member, String declaredType, String convertedType) {
        return requirePart(subject) + ARROW + kind + DOT + requirePart(member) + DOT + requirePart(declaredType) + DOT
                + requirePart(convertedType);
    }

    private static String requirePart(String part) {
        if (part.contains(ARROW) || part.contains(DOT) || part.contains("\n") || part.contains("\r")) {
            throw new IllegalStateException("Cannot write the binding manifest: \"" + part
                    + "\" contains a separator (\"" + ARROW.strip() + "\" or \"" + DOT.strip() + "\") or a line break.");
        }
        return part;
    }

    /**
     * @param header the header lines after {@link #FORMAT}: the binder, the graphql-java version, the schema hash
     * @param lines  the sorted body lines
     */
    public record Rendered(List<String> header, List<String> lines) {

        public Rendered {
            header = List.copyOf(header);
            lines = List.copyOf(lines);
        }
    }
}
