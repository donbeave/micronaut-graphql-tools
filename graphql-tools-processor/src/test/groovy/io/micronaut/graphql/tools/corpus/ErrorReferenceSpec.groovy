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

import io.micronaut.graphql.tools.visitor.ProcessorCodeScenarios
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * The example messages of the error reference (the guide page {@code exceptions.adoc}). A block that follows a
 * {@code // corpus: <case>} line must be the message that the startup of that Java case reports, so that no message of
 * the page is written from memory. A block that follows a {@code // compile: <scenario>} line must be the message of the
 * compile error of that scenario of {@link ProcessorCodeScenarios}, for the codes that only the annotation processor raises. The directory of the case is written as {@code <case directory>}. A block that follows a {@code // corpus-kotlin: <case>} line is the message of a Kotlin case, which {@link KotlinErrorReferenceSpec} compiles and starts.
 */
class ErrorReferenceSpec extends Specification {

    static final Path PAGE = Path.of(System.getProperty('guide.errors', '../src/main/docs/guide/exceptions.adoc'))

    @Shared
    Corpus corpus = new Corpus(CorpusFixtures.root())

    @Shared
    Map<String, String> examples = readExamples()

    void "the page has examples"() {
        expect:
        examples.size() >= 10
    }

    @Unroll
    void "the example of #caseName is the message of the startup"() {
        given:
        CorpusCase corpusCase = corpus.byName(caseName)
        Path work = Files.createTempDirectory('error-reference-')

        when:
        JavaCaseCompiler compiler = new JavaCaseCompiler()
        CompiledCase compiled = compiler.compile(corpusCase, work.resolve('unchecked'))
        Map<String, Object> result = new RuntimeBinder().start(new BindRequest(corpusCase, Language.JAVA, compiler, compiled, work))

        then:
        compiled.success
        ((String) result.get('message')).replace(corpusCase.dir.toAbsolutePath().toString(), '<case directory>')
                .readLines()*.stripTrailing().join('\n') == expected

        cleanup:
        work.toFile().deleteDir()

        where:
        [caseName, expected] << examples.collect { [it.key, it.value] }
    }

    @Shared
    Map<String, String> compileExamples = readExamples('// compile: ')

    @Unroll
    void "the example of the compile-time scenario #scenario is the message of the compile error"() {
        expect:
        ProcessorCodeScenarios.message(scenario) == expected

        where:
        [scenario, expected] << readExamples('// compile: ').collect { [it.key, it.value] }
    }

    void "the page has an example for each scenario of the compile-time harness"() {
        expect:
        compileExamples.keySet() == ProcessorCodeScenarios.CODES.keySet()
    }

    static Map<String, String> readExamples() {
        return readExamples('// corpus: ')
    }

    static Map<String, String> readExamples(String marker) {
        List<String> lines = Files.readAllLines(PAGE)
        Map<String, String> found = new LinkedHashMap<>()
        for (int i = 0; i < lines.size(); i++) {
            if (lines[i].startsWith(marker)) {
                int open = (i + 1..<lines.size()).find { lines[it] == '----' }
                int close = (open + 1..<lines.size()).find { lines[it] == '----' }
                found.put(lines[i].substring(marker.length()).trim(), lines.subList(open + 1, close)*.stripTrailing().join('\n'))
            }
        }
        return found
    }

}
