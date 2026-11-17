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

    boolean isBound() {
        return available && findings.empty
    }

    /** @return the lines of an expected file: {@code ok} for a bound verdict */
    List<String> lines() {
        return findings.empty ? ['ok'] : findings*.line()
    }

    Set<String> keys() {
        return findings*.key() as Set<String>
    }

    @Override
    String toString() {
        return available ? lines().join('\n') : 'unavailable'
    }

}
