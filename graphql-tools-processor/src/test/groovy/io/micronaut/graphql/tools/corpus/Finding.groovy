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
import groovy.transform.EqualsAndHashCode

/**
 * One line of a verdict: {@code <code> <coordinate> <schema-file>:<line>:<col>}. The code is a check name
 * for the checks and the simple name of the startup exception for the failures, until #237 lands. The coordinate is
 * the schema coordinate where the finding has one, the Java member or the schema type name where it has not. The
 * location is {@code -} when the finding has none.
 *
 * <p>Two findings are the same verdict when code and coordinate are equal ({@link #key()}); the location is part of
 * the recorded expectation but is not compared across the binders.</p>
 *
 * <p>A compile-time finding can be a {@link #warning}: the line starts with {@code warning }. A check that cannot prove its fact
 * from one class reports it as a warning (a class made introspected by another class, see the soundness rule), and the startup decides. A
 * warning is not a compile error, so it is outside the soundness rule, and it counts as the compile side having seen the
 * finding for the completeness rule.</p>
 */
@CompileStatic
@EqualsAndHashCode
final class Finding implements Comparable<Finding> {

    static final String WARNING = 'warning'

    final String code
    final String coordinate
    final String location
    final boolean warning

    Finding(String code, String coordinate, String location = '-', boolean warning = false) {
        this.code = code
        this.coordinate = coordinate
        this.location = location
        this.warning = warning
    }

    static Finding parse(String line) {
        String text = line.trim()
        boolean warning = text.startsWith(WARNING + ' ')
        List<String> parts = (warning ? text.substring(WARNING.length() + 1) : text).split(/\s+/, 3).toList()
        if (parts.size() != 3) {
            throw new IllegalArgumentException("Not a finding line ([warning ]<code> <coordinate> <location>): '${line}'")
        }
        return new Finding(parts[0], parts[1], parts[2], warning)
    }

    /** @return code and coordinate: what the binders must agree on */
    String key() {
        return code + ' ' + coordinate
    }

    String line() {
        return "${warning ? WARNING + ' ' : ''}${code} ${coordinate} ${location}".toString()
    }

    @Override
    int compareTo(Finding other) {
        return line() <=> other.line()
    }

    @Override
    String toString() {
        return line()
    }

}
