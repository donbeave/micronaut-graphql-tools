package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * A {@link CaseCompiler} that can also compile a case with the compile-time checks on, in the way a build does: the schema
 * directory is the option of the visitor, and the errors and warnings of the compiler are the findings. The plain
 * {@link #compile} is the same compilation with {@code checks=false}, which is what the runner starts.
 */
@CompileStatic
interface CheckedCaseCompiler extends CaseCompiler {

    /**
     * @param corpusCase the case
     * @param work       an empty directory that belongs to this call
     * @return the output, with the errors and warnings that the checks reported
     */
    CompiledCase compileChecked(CorpusCase corpusCase, Path work)

}
