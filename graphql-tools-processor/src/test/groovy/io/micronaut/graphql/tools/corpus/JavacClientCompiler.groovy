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
import io.micronaut.graphql.tools.visitor.VisitorCompilation

import java.nio.file.Files
import java.nio.file.Path

/** javac in process, with the real {@code -A} options, over the test class path. */
@CompileStatic
final class JavacClientCompiler implements ClientCaseCompiler {

    @Override
    Language getLanguage() {
        return Language.JAVA
    }

    @Override
    ClientCompiled compile(ClientCorpusCase corpusCase, Path work) {
        ClientCaseCompiler.Setup setup = new ClientCaseCompiler.Setup(corpusCase, work)
        VisitorCompilation compilation = new VisitorCompilation().withoutSchemaOption()
        Path root = corpusCase.dir.resolve(Language.JAVA.dir)
        corpusCase.sources(Language.JAVA).each { Path source ->
            compilation.source(root.relativize(source).toString().replace(File.separator, '.').replaceFirst(/\.java$/, ''), Files.readString(source))
        }
        setup.options.each { String name, String value -> compilation.option(name, value) }
        VisitorCompilation.Result result = compilation.compile()
        return new ClientCompiled(result.success, result.errors, [result.classOutput.toPath()], setup.clientDir, setup.schemaDir)
    }

}
