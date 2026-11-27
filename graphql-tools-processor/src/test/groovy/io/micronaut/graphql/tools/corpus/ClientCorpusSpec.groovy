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

import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path

/**
 * The client corpus: every case of {@code src/test/resources/client-corpus} compiled in process with javac and with the Groovy
 * compiler, the findings and the written metadata compared with the recorded ones. Kotlin under KSP is {@link KotlinClientCorpusSpec}, the
 * Gradle task {@code corpusKotlin}. Adding a case is adding a directory; see the README of the corpus.
 */
class ClientCorpusSpec extends Specification {

    static final Path ROOT = Path.of(System.getProperty('client.corpus.dir', 'src/test/resources/client-corpus')).toAbsolutePath()

    @Shared
    ClientCorpusRunner runner = new ClientCorpusRunner(new ClientCorpus(ROOT), [new JavacClientCompiler(), new GroovyClientCompiler()],
            Boolean.getBoolean('client.corpus.update'))

    @IgnoreIf({ Boolean.getBoolean('client.corpus.update') })
    void "the client corpus is well formed: no orphan, no unknown file, the same interfaces in every language"() {
        expect:
        runner.corpus.problems == []
        !runner.corpus.cases.empty
    }

    void "the client corpus covers every client code"() {
        given:
        Set<String> codes = runner.corpus.cases.collectMany { ClientCorpusCase c -> (c.expected('compile.txt') ?: '').readLines().collect { it.split(' ')[0] } }.toSet()

        expect:
        io.micronaut.graphql.tools.binding.BindingCode.values().findAll { it.code.startsWith('client-') }*.code.every { codes.contains(it) }
    }

    @Unroll
    void "#corpusCase.name [#language.dir]"() {
        expect:
        runner.run(corpusCase, language)

        where:
        [corpusCase, language] << runner.units()
    }

}
