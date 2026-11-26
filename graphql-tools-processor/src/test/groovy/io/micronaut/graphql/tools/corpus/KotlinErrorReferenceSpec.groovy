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
import java.util.regex.Pattern

/**
 * The Kotlin examples of the error reference: a block that follows a {@code // corpus-kotlin: <case>} line of the guide page
 * must be the message that the startup of that Kotlin case reports. The cases are compiled in one Gradle TestKit build, as in
 * {@link KotlinCorpusSpec}, so this is part of the Gradle task {@code corpusKotlin}, not of {@code test}.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class KotlinErrorReferenceSpec extends Specification {

    @Shared
    Corpus corpus = new Corpus(CorpusFixtures.root())

    @Shared
    Map<String, String> examples = ErrorReferenceSpec.readExamples('// corpus-kotlin: ')

    @Shared
    KotlinBatchCompiler kotlin = new KotlinBatchCompiler(corpus, Path.of(System.getProperty('corpus.kotlin.errors.dir', 'build/corpus-kotlin-errors')).toAbsolutePath(),
            KotlinBatchCompiler.Processor.KSP, '^(' + examples.keySet().collect { Pattern.quote(it) }.join('|') + ')$')

    void "the page has Kotlin examples"() {
        expect:
        !examples.empty
    }

    @Unroll
    void "the example of #caseName is the message of the startup"() {
        given:
        CorpusCase corpusCase = corpus.byName(caseName)
        Path work = Files.createTempDirectory('error-reference-kotlin-')

        when:
        CompiledCase compiled = kotlin.compile(corpusCase, work.resolve('unchecked'))
        Map<String, Object> result = new RuntimeBinder().start(new BindRequest(corpusCase, Language.KOTLIN, kotlin, compiled, work))

        then:
        compiled.success
        ((String) result.get('message')).replace(corpusCase.dir.toAbsolutePath().toString(), '<case directory>')
                .readLines()*.stripTrailing().join('\n') == expected

        cleanup:
        work.toFile().deleteDir()

        where:
        [caseName, expected] << ErrorReferenceSpec.readExamples('// corpus-kotlin: ').collect { [it.key, it.value] }
    }

}
