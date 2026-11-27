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

/**
 * Runs a client case in a language: compiles the client interfaces with the client visitor, and compares the findings and the
 * metadata that the visitor wrote with {@code expected/compile.txt} and {@code expected/metadata.txt}. With {@code update} the first language
 * of the case (Java, but for a case of one language) rewrites the two files from what it saw, and the other languages are compared with them, so that the
 * languages cannot drift.
 */
@CompileStatic
final class ClientCorpusRunner {

    final ClientCorpus corpus
    private final Map<Language, ClientCaseCompiler> compilers = [:]
    private final boolean update

    ClientCorpusRunner(ClientCorpus corpus, List<ClientCaseCompiler> compilers, boolean update) {
        this.corpus = corpus
        compilers.each { this.compilers.put(it.language, it) }
        this.update = update
    }

    /** @return the cases and languages that this runner has a compiler for */
    List<List<Object>> units() {
        String only = CorpusFixtures.only()
        return corpus.units().findAll { compilers.containsKey((Language) it[1]) && (only == null || ((ClientCorpusCase) it[0]).name ==~ only) }
    }

    boolean run(ClientCorpusCase corpusCase, Language language) {
        Path work = Files.createTempDirectory('client-corpus-')
        try {
            ClientCompiled compiled = compilers.get(language).compile(corpusCase, work)
            String compile = ClientVerdicts.compile(compiled)
            String metadata = compile == 'ok' ? ClientVerdicts.metadata(compiled, corpusCase.clients(language)) : null
            if (update && language == corpusCase.languages().first()) {
                write(corpusCase, 'compile.txt', compile)
                if (metadata != null) {
                    write(corpusCase, 'metadata.txt', metadata)
                } else {
                    Files.deleteIfExists(corpusCase.expectedFile('metadata.txt'))
                }
                return true
            }
            assert compile == corpusCase.expected('compile.txt'): "${corpusCase.name} [${language.dir}]: the findings differ from expected/compile.txt.\nexpected:\n${corpusCase.expected('compile.txt')}\nactual:\n${compile}".toString()
            if (compile == 'ok') {
                assert metadata == corpusCase.expected('metadata.txt'): "${corpusCase.name} [${language.dir}]: the metadata differs from expected/metadata.txt.\nexpected:\n${corpusCase.expected('metadata.txt')}\nactual:\n${metadata}".toString()
            }
            return true
        } finally {
            work.toFile().deleteDir()
        }
    }

    private static void write(ClientCorpusCase corpusCase, String name, String text) {
        Files.createDirectories(corpusCase.expectedFile(name).parent)
        Files.writeString(corpusCase.expectedFile(name), text + '\n')
    }

}
