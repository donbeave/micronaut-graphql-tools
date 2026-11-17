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
package io.micronaut.graphql.tools;

import graphql.schema.idl.SchemaDirectiveWiring;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A bean of this type is how the library applies a {@link SchemaDirectiveWiring} and says which SDL directives it
 * implements. It comes in two kinds, matching graphql-java's two registrations:
 *
 * <ul>
 *   <li>{@link #named(String, SchemaDirectiveWiring)}: graphql-java calls the wiring only for the schema elements that
 *   apply that directive ({@code RuntimeWiring.Builder#directive(String, SchemaDirectiveWiring)});</li>
 *   <li>{@link #everyElement(Collection, SchemaDirectiveWiring)}: graphql-java calls the wiring for every schema
 *   element, and the wiring decides itself what to do ({@code RuntimeWiring.Builder#directiveWiring}); the names say
 *   which directives it implements. graphql-java-extended-validation's {@code ValidationSchemaWiring} is such a
 *   wiring.</li>
 * </ul>
 *
 * <p>A {@link SchemaDirectiveWiring} bean is not applied on its own: if no {@code SchemaDirective} bean wraps it (the
 * same instance), the startup fails with
 * {@link io.micronaut.graphql.tools.exceptions.UnwrappedDirectiveWiringException}, which names it. A wiring thus never
 * runs on every schema element merely because it is a bean; a wiring that is a bean is wrapped by injecting it into
 * the factory method of its {@code SchemaDirective}.</p>
 *
 * <p>Every directive the schema applies needs a {@code SchemaDirective} that names it, or must be listed in
 * {@code micronaut.graphql.tools.inert-directives}.</p>
 *
 * <p>For example, a factory method {@code SchemaDirective upper()} annotated {@code @Singleton} that returns
 * {@code SchemaDirective.named("upper", new UpperCaseWiring())}.</p>
 */
public final class SchemaDirective {

    private final Set<String> directiveNames;
    private final SchemaDirectiveWiring wiring;
    private final boolean everyElement;

    private SchemaDirective(Set<String> directiveNames, SchemaDirectiveWiring wiring, boolean everyElement) {
        this.directiveNames = directiveNames;
        this.wiring = wiring;
        this.everyElement = everyElement;
    }

    /**
     * @param name   the directive name, without {@code @}
     * @param wiring the wiring graphql-java calls for the elements that apply the directive
     * @return the binding
     */
    @NonNull
    public static SchemaDirective named(@NonNull String name, @NonNull SchemaDirectiveWiring wiring) {
        ArgumentUtils.requireNonNull("wiring", wiring);
        return new SchemaDirective(Set.of(requireDirectiveName(name)), wiring, false);
    }

    /**
     * @param directiveNames the directives the wiring implements, without {@code @}; at least one
     * @param wiring         the wiring graphql-java calls for every schema element
     * @return the binding
     */
    @NonNull
    public static SchemaDirective everyElement(@NonNull Collection<String> directiveNames,
                                               @NonNull SchemaDirectiveWiring wiring) {
        ArgumentUtils.requireNonNull("directiveNames", directiveNames);
        ArgumentUtils.requireNonNull("wiring", wiring);
        if (directiveNames.isEmpty()) {
            throw new IllegalArgumentException("directiveNames must name at least one directive");
        }
        Set<String> names = new LinkedHashSet<>();
        directiveNames.forEach(it -> names.add(requireDirectiveName(it)));
        return new SchemaDirective(Collections.unmodifiableSet(names), wiring, true);
    }

    private static String requireDirectiveName(String name) {
        ArgumentUtils.requireNonNull("name", name);
        if (name.isBlank() || name.startsWith("@")) {
            throw new IllegalArgumentException("name must be a directive name without @: '" + name + "'");
        }
        return name;
    }

    /**
     * @return the directive name of a {@link #named(String, SchemaDirectiveWiring) named} wiring, without {@code @};
     * {@code null} for a wiring of {@link #everyElement(Collection, SchemaDirectiveWiring) every element}
     */
    @Nullable
    public String getName() {
        return everyElement ? null : directiveNames.iterator().next();
    }

    /**
     * @return the directives the wiring implements, without {@code @}
     */
    @NonNull
    public Set<String> getDirectiveNames() {
        return directiveNames;
    }

    /**
     * @return whether graphql-java calls the wiring for every schema element rather than only where the directive is
     * applied
     */
    public boolean isEveryElement() {
        return everyElement;
    }

    /**
     * @return the wiring
     */
    @NonNull
    public SchemaDirectiveWiring getWiring() {
        return wiring;
    }

    @Override
    public String toString() {
        return "SchemaDirective{" + (everyElement ? "every element, " : "") + "@"
                + String.join(", @", directiveNames) + " -> " + wiring.getClass().getName() + "}";
    }

}
