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
