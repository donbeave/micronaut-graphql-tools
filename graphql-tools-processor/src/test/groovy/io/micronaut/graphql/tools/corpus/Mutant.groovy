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
package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

/**
 * One change of the schema of a case: the SDL files that replace the case's own, and what the change is meant to do. A breaking
 * mutant desynchronises schema and code, so the startup and the compile-time check must fail and name the changed coordinate; an
 * equivalent mutant says the same thing in another way (a field moved into an extension, arguments in another order, the definitions
 * in another order), so the startup must bind to the same manifest and answer the same introspection. Issue #89.
 */
@CompileStatic
final class Mutant {

    final String id
    final String kind
    final boolean breaking
    /** the schema coordinate that the mutant changes, or the type or file for a mutant that changes an order */
    final String coordinate
    /** fragments of a finding's coordinate that name the mutated element: the schema coordinate, a Java member */
    final List<String> names
    /** the SDL files by file name, in the order they are written to the case directory */
    final Map<String, String> files
    /** the element that the unchanged case has to bind for the mutant to be a drift: {@code Type.field}, {@code Enum.VALUE}, the type for a new property */
    final String element

    Mutant(String kind, boolean breaking, String coordinate, List<String> names, Map<String, String> files, String element = null) {
        this.id = kind + ' ' + coordinate
        this.kind = kind
        this.breaking = breaking
        this.coordinate = coordinate
        this.names = Collections.unmodifiableList(new ArrayList<>(names))
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files))
        int paren = coordinate.indexOf('(')
        this.element = element != null ? element : paren < 0 ? coordinate : coordinate.substring(0, paren)
    }

    /** @return whether a finding is about the mutated element */
    boolean names(Finding finding) {
        return names.any { String name ->
            name.startsWith('.') ? finding.coordinate.endsWith(name) : name.startsWith('#') ? (name.endsWith('(') ? finding.coordinate.contains(name) : finding.coordinate.endsWith(name))
                    : finding.coordinate == name || finding.coordinate.startsWith(name + '(')
        }
    }

    @Override
    String toString() {
        return id
    }

}
