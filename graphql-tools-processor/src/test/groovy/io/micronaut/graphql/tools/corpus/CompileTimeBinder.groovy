package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

/**
 * The compile-time backend. The visitor of #150 implements this interface (it compiles the case with the checks on, using
 * {@link BindRequest#compiler} for the sources and its own processing for the checks, and returns the errors it
 * reports as findings with the same code and coordinate the startup uses), and {@link CorpusRunner} is constructed with it.
 *
 * <p>Until then {@link NoCompileTimeBinder#INSTANCE} answers {@link Verdict#unavailable()}: the runner then asserts only the recorded
 * expectations of {@code compile.txt} against {@code runtime.txt} (soundness and completeness), not a compilation.</p>
 */
@CompileStatic
interface CompileTimeBinder extends Binder {

}
