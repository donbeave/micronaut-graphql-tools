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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Set;

/**
 * The verification gate of the metadata channel: an isolating visitor writes the internal annotation of
 * the contracts into the annotation metadata of a class with {@code Element#annotate}, and ContractsGateSpec shows that
 * the bean definition carries it at runtime in each language. It acts only on a class that carries the marker annotation of the
 * spec, so that it is inert in every other compilation of the test classpath.
 */
public final class ContractsGateVisitor implements TypeElementVisitor<Object, Object> {

    static final String MARKER = "gate.Marked";

    static final String CONTRACTS = "io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(MARKER);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(MARKER)) {
            element.annotate(CONTRACTS, builder -> builder
                    .member("value", new AnnotationClassValue[] {new AnnotationClassValue<>("java.lang.String")})
                    .member("declaredBy", new String[] {"gate.Contract"}));
        }
    }
}
