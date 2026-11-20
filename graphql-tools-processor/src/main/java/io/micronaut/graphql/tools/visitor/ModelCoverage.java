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

import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The classes that {@code @GraphQLModels} makes introspected in one compilation: the listed classes, and the classes of the
 * listed packages that carry one of the annotations the annotation names. The annotated class can be visited after the
 * resolver that returns a model, so the visitor judges the classes that were not covered again when the compilation
 * finishes, from what it recorded about them (a class element is not valid after its round).
 */
final class ModelCoverage {

    private final Set<String> classes = new HashSet<>();
    private final List<PackageEntry> packages = new ArrayList<>();
    private final List<Missed> missed = new ArrayList<>();

    void add(Iterable<String> classNames, Iterable<String> packageNames, Iterable<String> markerNames) {
        classNames.forEach(classes::add);
        Set<String> markers = new HashSet<>();
        markerNames.forEach(markers::add);
        packageNames.forEach(it -> packages.add(new PackageEntry(it, markers)));
    }

    /**
     * @param binaryName  the binary name of a class
     * @param annotations the binary names of the annotations of the class
     * @param scanned     whether Micronaut scans the class for a listed package; it does not for a Groovy class or a class that is not public
     * @return whether a {@code @GraphQLModels} of this compilation lists the class, or lists its package and an annotation
     * that the class carries
     */
    boolean covers(String binaryName, Set<String> annotations, boolean scanned) {
        int dot = binaryName.lastIndexOf('.');
        String packageName = dot > 0 ? binaryName.substring(0, dot) : "";
        if (classes.contains(binaryName) || scanned && packages.stream().anyMatch(it -> it.name.equals(packageName)
                && it.markers.stream().anyMatch(annotations::contains))) {
            return true;
        }
        missed.add(new Missed(binaryName, annotations, scanned));
        return false;
    }

    boolean covers(Missed missed) {
        return covers(missed.name, missed.annotations, missed.scanned);
    }

    void resetMissed() {
        missed.clear();
    }

    /**
     * @param sentence the sentence of a finding
     * @return the class that a judgement since {@link #resetMissed()} found not covered and that the sentence names, or
     * {@code null}
     */
    @Nullable
    Missed missedIn(String sentence) {
        return missed.stream().filter(it -> names(sentence, it.name())).findFirst().orElse(null);
    }

    // the whole class name: the next character must not continue a name, so Book is not found in BookDetails or Book$Inner
    private static boolean names(String sentence, String name) {
        for (int at = sentence.indexOf(name); at >= 0; at = sentence.indexOf(name, at + 1)) {
            int end = at + name.length();
            if (end == sentence.length() || !Character.isJavaIdentifierPart(sentence.charAt(end))) {
                return true;
            }
        }
        return false;
    }

    private record PackageEntry(String name, Set<String> markers) {
    }

    /**
     * A class that no {@code @GraphQLModels} covered when it was judged.
     *
     * @param name        the binary name
     * @param annotations the binary names of the annotations of the class
     * @param scanned     whether Micronaut scans the class for a listed package
     */
    record Missed(String name, Set<String> annotations, boolean scanned) {
    }

}
