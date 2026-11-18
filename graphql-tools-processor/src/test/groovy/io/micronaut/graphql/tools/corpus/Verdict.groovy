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
 * What one binder says about one case in one language: bound, or the sorted findings. {@link #unavailable()} is what a
 * backend that does not exist yet says.
 */
@CompileStatic
final class Verdict {

    final boolean available
    final List<Finding> findings
    /** the binding manifest of a bound startup (without its `#` header lines, which name the versions), or {@code null} */
    final String manifest

    private Verdict(boolean available, List<Finding> findings, String manifest) {
        this.available = available
        this.findings = Collections.unmodifiableList(new ArrayList<>(findings.sort(false)))
        this.manifest = manifest
    }

    static Verdict bound(String manifest = null) {
        return new Verdict(true, [], manifest)
    }

    static Verdict failed(List<Finding> findings) {
        if (findings.empty) {
            throw new IllegalArgumentException('A failed verdict needs at least one finding')
        }
        return new Verdict(true, findings, null)
    }

    static Verdict unavailable() {
        return new Verdict(false, [], null)
    }

    /** @return whether nothing failed: no finding, or only warnings (a compile-time verdict that lets the build pass) */
    boolean isBound() {
        return available && findings.every { it.warning }
    }

    /** @return the findings that are errors */
    List<Finding> getErrors() {
        return findings.findAll { !it.warning }
    }

    /** @return the lines of an expected file: {@code ok} for a bound verdict */
    List<String> lines() {
        return findings.empty ? ['ok'] : findings*.line()
    }

    /** @return code and coordinate of every finding, warnings included */
    Set<String> keys() {
        return findings*.key() as Set<String>
    }

    /** @return code and coordinate of the findings that are errors */
    Set<String> errorKeys() {
        return errors*.key() as Set<String>
    }

    @Override
    String toString() {
        return available ? lines().join('\n') : 'unavailable'
    }

}
