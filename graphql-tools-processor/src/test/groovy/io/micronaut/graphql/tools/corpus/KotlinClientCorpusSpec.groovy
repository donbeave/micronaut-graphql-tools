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
 * The Kotlin side of the client corpus: every case with a {@code kotlin/} directory, compiled in one batched Gradle TestKit build with
 * Kotlin and KSP2 (kapt with {@code -Pcorpus.kotlin.processor=kapt}, for the {@code lang:all} cases), the findings and the written
 * metadata compared with the recorded ones. It is part of the Gradle task {@code corpusKotlin}, not of {@code test}: it needs the network unless the
 * Gradle user home is warm. The wall time of the build is written to {@code build/corpus/kotlin-client-timings.txt}.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class KotlinClientCorpusSpec extends Specification {

    static final Path ROOT = Path.of(System.getProperty('client.corpus.dir', 'src/test/resources/client-corpus')).toAbsolutePath()

    @Shared
    ClientCorpus corpus = new ClientCorpus(ROOT)
    @Shared
    KotlinBatchCompiler.Processor processor = KotlinBatchCompiler.Processor.fromProperty()
    @Shared
    KotlinClientBatch kotlin = new KotlinClientBatch(corpus, Path.of(System.getProperty('corpus.kotlin.client.dir', 'build/corpus-kotlin-client')).toAbsolutePath(), processor)
    @Shared
    ClientCorpusRunner runner = new ClientCorpusRunner(corpus, [kotlin], Boolean.getBoolean('client.corpus.update'))

    @Unroll
    void "#corpusCase.name [kotlin]"() {
        expect:
        runner.run(corpusCase, Language.KOTLIN)

        where:
        [corpusCase, language] << new ClientCorpus(ROOT).units().findAll {
            it[1] == Language.KOTLIN && KotlinClientBatch.covers(KotlinBatchCompiler.Processor.fromProperty(), (ClientCorpusCase) it[0]) &&
                    (CorpusFixtures.only() == null || ((ClientCorpusCase) it[0]).name ==~ CorpusFixtures.only())
        }
    }

    void "the batch ran and stays within the budget"() {
        expect:
        !runner.units().empty
        kotlin.buildMillis >= 0
        // one Gradle project per case: about 3.3 s of a loaded CI runner each (294 s for 90 cases), so the budget grows with the corpus
        assert kotlin.buildMillis <= 420_000: "the Kotlin client batch took ${kotlin.buildMillis} ms, over the 7 minute budget"
    }

    void cleanupSpec() {
        Path report = CorpusFixtures.reportFile().resolveSibling('kotlin-client-timings.txt')
        Files.createDirectories(report.parent)
        Files.writeString(report, "gradle build of ${runner.units().size()} kotlin client cases: ${kotlin.buildMillis} ms\n")
    }

}
