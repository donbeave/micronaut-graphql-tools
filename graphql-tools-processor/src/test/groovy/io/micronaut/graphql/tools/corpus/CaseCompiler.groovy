package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Compiles the sources of one language of a case, with Micronaut's own processing only (bean definitions and
 * introspections) so that the result can be started. The compile-time checks of this library are not applied here: a
 * {@link CompileTimeBinder} runs its own compilation with them.
 */
@CompileStatic
interface CaseCompiler {

    Language getLanguage()

    /**
     * @param corpusCase the case
     * @param work       an empty directory that belongs to this call
     * @return the class output, or the compile errors of a source that does not compile
     */
    CompiledCase compile(CorpusCase corpusCase, Path work)

}
