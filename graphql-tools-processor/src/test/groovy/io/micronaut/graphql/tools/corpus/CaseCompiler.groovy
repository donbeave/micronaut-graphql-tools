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

    /**
     * @param corpusCase the case
     * @return the expected file that holds the findings of the checked compilation: {@code compile.txt}, or the file of the case
     * that says what this compiler reports where it differs
     */
    default String compileVerdictFile(CorpusCase corpusCase) {
        return 'compile.txt'
    }

}
