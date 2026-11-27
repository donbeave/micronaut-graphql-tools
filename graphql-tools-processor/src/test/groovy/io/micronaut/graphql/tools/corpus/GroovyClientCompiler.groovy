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

import java.nio.file.Files
import java.nio.file.Path

/** The Groovy compiler in process, with Micronaut's AST transformation; the options are system properties. */
@CompileStatic
final class GroovyClientCompiler implements ClientCaseCompiler {

    @Override
    Language getLanguage() {
        return Language.GROOVY
    }

    @Override
    ClientCompiled compile(ClientCorpusCase corpusCase, Path work) {
        ClientCaseCompiler.Setup setup = new ClientCaseCompiler.Setup(corpusCase, work)
        Path classes = Files.createDirectories(work.resolve('classes'))
        List<Object> result = GroovyCaseCompiler.run(corpusCase.sources(Language.GROOVY).collect { it.toString() }, classes.toString(), [],
                setup.options)
        return new ClientCompiled((boolean) result[0], (List<String>) result[1], [classes], setup.clientDir, setup.schemaDir)
    }

}
