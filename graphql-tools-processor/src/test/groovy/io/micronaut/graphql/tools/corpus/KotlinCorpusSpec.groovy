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

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * The Kotlin side of the binding corpus: every case with a {@code kotlin/} directory, compiled in one batched
 * Gradle TestKit build with Kotlin and KSP2, then started and judged exactly like the in-process cases. It is the Gradle
 * task {@code corpusKotlin}, not part of {@code test}: it needs the network (plugin portal, Maven Central) unless the Gradle
 * user home is warm, and its budget is three minutes on JDK 25. The wall time of the build is written to
 * {@code build/corpus/kotlin-timings.txt}.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class KotlinCorpusSpec extends Specification {

    static final long BUDGET_SECONDS = Long.getLong('corpus.kotlin.budget-seconds', 180L)

    @Shared
    Corpus corpus = new Corpus(CorpusFixtures.root())
    @Shared
    KotlinBatchCompiler kotlin = new KotlinBatchCompiler(corpus, Path.of(System.getProperty('corpus.kotlin.dir', 'build/corpus-kotlin')).toAbsolutePath())
    @Shared
    CorpusRunner runner = new CorpusRunner(corpus, [kotlin], new RuntimeBinder(), NoCompileTimeBinder.INSTANCE, CorpusFixtures.update())

    @Unroll
    void "#corpusCase.name [kotlin]"() {
        expect:
        runner.run(corpusCase, Language.KOTLIN)

        where:
        [corpusCase, language] << new Corpus(CorpusFixtures.root()).units().findAll { it[1] == Language.KOTLIN }
    }

    void "the batch stays within the budget"() {
        expect:
        kotlin.buildMillis >= 0
        kotlin.buildMillis <= BUDGET_SECONDS * 1000
    }

    void cleanupSpec() {
        Path report = CorpusFixtures.reportFile().resolveSibling('kotlin-timings.txt')
        Files.createDirectories(report.parent)
        Files.writeString(report, "gradle build of ${runner.units().size()} kotlin cases: ${kotlin.buildMillis} ms\n" + runner.report())
    }

}
