package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * One line of a verdict: {@code <code> <coordinate> <schema-file>:<line>:<col>}. The code is a check name of D-30
 * for the checks and the simple name of the startup exception for the failures, until #237 lands. The coordinate is
 * the schema coordinate where the finding has one, the Java member or the schema type name where it has not. The
 * location is {@code -} when the finding has none.
 *
 * <p>Two findings are the same verdict when code and coordinate are equal ({@link #key()}); the location is part of
 * the recorded expectation but is not compared across the binders.</p>
 */
@CompileStatic
@EqualsAndHashCode
final class Finding implements Comparable<Finding> {

    final String code
    final String coordinate
    final String location

    Finding(String code, String coordinate, String location = '-') {
        this.code = code
        this.coordinate = coordinate
        this.location = location
    }

    static Finding parse(String line) {
        List<String> parts = line.trim().split(/\s+/, 3).toList()
        if (parts.size() != 3) {
            throw new IllegalArgumentException("Not a finding line (<code> <coordinate> <location>): '${line}'")
        }
        return new Finding(parts[0], parts[1], parts[2])
    }

    /** @return code and coordinate: what the binders must agree on */
    String key() {
        return code + ' ' + coordinate
    }

    String line() {
        return "${code} ${coordinate} ${location}".toString()
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
