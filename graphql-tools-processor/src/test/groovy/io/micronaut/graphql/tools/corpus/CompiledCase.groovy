package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * The outcome of compiling one language of a case: a class output that can be started, or the errors of a source that
 * does not compile (a mistake in the case, reported by the runner).
 */
@CompileStatic
final class CompiledCase {

    final boolean success
    final Path classes
    final List<String> errors
    /** further class and resource directories of the output, for a build that writes them apart (Kotlin with KSP) */
    final List<Path> more
    /** the warnings of the compiler, which only a checked compilation is read for */
    final List<String> warnings

    CompiledCase(boolean success, Path classes, List<String> errors, List<Path> more = [], List<String> warnings = []) {
        this.success = success
        this.classes = classes
        this.errors = errors
        this.more = more
        this.warnings = warnings
    }

}
