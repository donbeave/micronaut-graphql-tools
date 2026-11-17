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

/**
 * The errors of one compilation. Each one starts with {@value #PREFIX} and the schema coordinate it is about, ends
 * with the schema file and line, and is reported on the Java element it is about, if there is one, so that javac
 * prints the Java file and line too. An error is reported once, in the order found.
 */
final class Problems {

    /**
     * The prefix of every message.
     */
    static final String PREFIX = "[graphql] ";

    private final Set<Problem> problems = new LinkedHashSet<>();
    private final List<String> notes = new ArrayList<>();

    /**
     * Records an error that is about neither a schema element nor a Java element.
     *
     * @param message the message
     */
    void error(String message) {
        problems.add(new Problem(PREFIX + message, null));
    }

    /**
     * Records an error.
     *
     * @param coordinate the schema coordinate, such as {@code Query.book} or {@code Book}
     * @param message    the message
     * @param node       the schema element, for its file and line, or {@code null}
     * @param element    the Java element, or {@code null}
     */
    void error(String coordinate, String message, Node<?> node, Element element) {
        problems.add(new Problem(PREFIX + coordinate + ": " + message + location(node), element));
    }

    /**
     * Records a part of the schema the checks leave to the startup, reported as a note, so that a green compilation
     * says what it did not check.
     *
     * @param coordinate the schema coordinate
     * @param reason     why it is not checked at compile time
     */
    void note(String coordinate, String reason) {
        notes.add(PREFIX + coordinate + ": not checked at compile time, the startup checks it: " + reason + ".");
    }

    /**
     * @return whether an error was recorded
     */
    boolean hasErrors() {
        return !problems.isEmpty();
    }

    /**
     * @return the messages recorded so far
     */
    List<String> messages() {
        List<String> result = new ArrayList<>();
        problems.forEach(it -> result.add(it.message()));
        return result;
    }

    /**
     * Reports every error as a javac error.
     *
     * @param messager the messager
     */
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

    /**
     * @param node a schema element
     * @return its file and line, as {@code " (schema.graphqls:12)"}, or an empty string
     */
    static String location(Node<?> node) {
        if (node == null || node.getSourceLocation() == null) {
            return "";
        }
        SourceLocation location = node.getSourceLocation();
        String source = location.getSourceName() != null ? location.getSourceName() : "schema";
        return " (" + source + ":" + location.getLine() + ")";
    }

    /**
     * One error.
     *
     * @param message the message
     * @param element the Java element it is reported on, or {@code null}
     */
    private record Problem(String message, Element element) {
    }

}
