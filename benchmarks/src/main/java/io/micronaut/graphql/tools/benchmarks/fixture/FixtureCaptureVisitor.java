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
package io.micronaut.graphql.tools.benchmarks.fixture;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Hands the {@link ClassElement} of every class that carries the fixture marker to the {@link ClassElementFixture} whose compilation
 * is running, and keeps the compilation inside its round until the fixture is closed: a class element is valid only during its round.
 * Registered as a service of the benchmark classpath; inert when no fixture is compiling.
 */
public final class FixtureCaptureVisitor implements TypeElementVisitor<Object, Object> {

    private final Map<String, ClassElement> captured = new LinkedHashMap<>();
    private boolean handedOver;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(BindingFixtureSources.MARKER);
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
        if (ClassElementFixture.compiling() != null && element.hasDeclaredAnnotation(BindingFixtureSources.MARKER)) {
            captured.put(element.getName(), element);
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        ClassElementFixture fixture = ClassElementFixture.compiling();
        if (fixture != null && !handedOver && !captured.isEmpty()) {
            handedOver = true;
            fixture.holdRound(Map.copyOf(captured));
        }
    }
}
