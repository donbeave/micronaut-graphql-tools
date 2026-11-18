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

import graphql.language.Node;
import graphql.language.SourceLocation;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Problems {

    static final String PREFIX = "[graphql] ";

    private final Set<Problem> problems = new LinkedHashSet<>();
    private final List<String> notes = new ArrayList<>();

    void error(String message) {
        problems.add(new Problem(PREFIX + message, null));
    }

    void error(String coordinate, String message, Node<?> node, Element element) {
        problems.add(new Problem(PREFIX + coordinate + ": " + message + location(node), element));
    }

    void note(String coordinate, String reason) {
        notes.add(PREFIX + coordinate + ": not checked at compile time, the startup checks it: " + reason + ".");
    }

    void info(String message) {
        notes.add(PREFIX + message);
    }

    boolean hasErrors() {
        return !problems.isEmpty();
    }

    List<String> messages() {
        List<String> result = new ArrayList<>();
        problems.forEach(it -> result.add(it.message()));
        return result;
    }

    void report(Messager messager) {
        for (String note : notes) {
            messager.printMessage(Diagnostic.Kind.NOTE, note);
        }
        for (Problem problem : problems) {
            if (problem.element() != null) {
                messager.printMessage(Diagnostic.Kind.ERROR, problem.message(), problem.element());
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, problem.message());
            }
        }
    }

    static String location(Node<?> node) {
        if (node == null || node.getSourceLocation() == null) {
            return "";
        }
        SourceLocation location = node.getSourceLocation();
        String source = location.getSourceName() != null ? location.getSourceName() : "schema";
        return " (" + source + ":" + location.getLine() + ")";
    }

    private record Problem(String message, Element element) {
    }

}
