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
 * Gradle TestKit build with Kotlin and KSP2 (kapt with {@code -Pcorpus.kotlin.processor=kapt}, for the {@code lang:all} cases),
 * twice per case like the in-process cases: with the checks off, and started; with the checks on, whose findings are compared
 * with {@code compile.txt}. It is the Gradle task {@code corpusKotlin}, not part of {@code test}: it needs the network (plugin
 * portal, Maven Central) unless the Gradle user home is warm, and its budget is eight minutes on JDK 25. The CI job
 * {@code build tools} runs it. The wall time of the build is written to {@code build/corpus/kotlin-timings.txt}.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class KotlinCorpusSpec extends Specification {

    static final long BUDGET_SECONDS = Long.getLong('corpus.kotlin.budget-seconds', 480L)

    @Shared
    Corpus corpus = new Corpus(CorpusFixtures.root())
    @Shared
    KotlinBatchCompiler.Processor processor = KotlinBatchCompiler.Processor.fromProperty()
    @Shared
    KotlinBatchCompiler kotlin = new KotlinBatchCompiler(corpus, Path.of(System.getProperty('corpus.kotlin.dir', 'build/corpus-kotlin')).toAbsolutePath(), processor)
    @Shared
    CorpusRunner runner = new CorpusRunner(corpus, [kotlin], new RuntimeBinder(), new VisitorCompileTimeBinder(), CorpusFixtures.update())

    // the one Gradle build of all the cases is a fixture of the spec, not of the first case: the time limit of a test (5 minutes, from the
    // test timeout of the build) is for a hung test, and the build is bounded by the budget below
    void setupSpec() {
        if (!runner.units().empty) {
            kotlin.prepare()
        }
    }

    @Unroll
    void "#corpusCase.name [kotlin]"() {
        expect:
        runner.run(corpusCase, Language.KOTLIN)

        where:
        [corpusCase, language] << new Corpus(CorpusFixtures.root()).units().findAll {
            it[1] == Language.KOTLIN && KotlinBatchCompiler.covers(KotlinBatchCompiler.Processor.fromProperty(), (CorpusCase) it[0]) &&
                    (CorpusFixtures.only() == null || ((CorpusCase) it[0]).name ==~ CorpusFixtures.only())
        }
    }

    @Unroll
    void "the visitor reports the JVM name of the internal method of #caseName as the startup does"() {
        given:
        CorpusCase corpusCase = corpus.byName(caseName)
        String suffix = '$' + KotlinBatchCompiler.moduleName(corpusCase).replace('-', '_') + '('

        when:
        List<String> compile = unbound(corpusCase.expected(Language.KOTLIN, kotlin.compileVerdictFile(corpusCase)))
        List<String> runtime = unbound(corpusCase.expected(Language.KOTLIN, 'runtime.txt'))

        then:
        !compile.empty
        compile == runtime
        compile.every { it.contains(suffix) }

        where:
        caseName << ['strict/kotlin-internal-method', 'strict/kotlin-internal-method-field']
    }

    private static List<String> unbound(String verdict) {
        return verdict.readLines().findAll { it.startsWith('unbound-resolver-method ') }
    }

    void "the batch ran and stays within the budget"() {
        expect:
        !runner.units().empty
        kotlin.buildMillis >= 0
        assert kotlin.buildMillis <= BUDGET_SECONDS * 1000 :
                "the Kotlin batch took ${kotlin.buildMillis} ms, over the ${BUDGET_SECONDS} s budget (corpus.kotlin.budget-seconds)"
    }

    void cleanupSpec() {
        Path report = CorpusFixtures.reportFile().resolveSibling('kotlin-timings.txt')
        Files.createDirectories(report.parent)
        Files.writeString(report, "gradle build of ${runner.units().size()} kotlin cases: ${kotlin.buildMillis} ms\n" + runner.report())
    }

}
