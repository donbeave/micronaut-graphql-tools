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

import java.nio.file.Files
import java.nio.file.Path

/**
 * The binding corpus (#142): every case of {@code src/test/resources/corpus} in every language the runner compiles in
 * process (Java and Groovy; the default graphql-java leg on JDK 21 and 25, see build.gradle.kts), with the verdicts recorded next to it. Adding a case is adding
 * a directory: this class does not change. See {@code CorpusRunner} for the assertions and
 * {@code src/test/resources/corpus/README.md} for how to add and update a case.
 */
class BindingCorpusSpec extends Specification {

    @Shared
    CorpusRunner runner = CorpusFixtures.runner()

    @IgnoreIf({ CorpusFixtures.update() })
    void "the corpus is well formed: no orphan or stale file, parity of the recorded verdicts, the divergence ceiling holds"() {
        expect:
        runner.corpus.problems == []
        !runner.corpus.cases.empty
    }

    @Unroll
    void "#corpusCase.name [#language.dir]"() {
        expect:
        runner.run(corpusCase, language)

        where:
        [corpusCase, language] << runner.units()
    }

    void cleanupSpec() {
        Path report = CorpusFixtures.reportFile()
        Files.createDirectories(report.parent)
        Files.writeString(report, runner.report())
    }

}
