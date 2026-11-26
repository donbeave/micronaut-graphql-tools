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
package io.micronaut.graphql.tools.visitor

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * The example messages of the client codes in the error reference (the guide page {@code exceptions.adoc}). A block that follows a
 * {@code // client: <scenario>} line must be the message that the compile error of that scenario of {@link ClientCodeScenarios}
 * carries. The sentence of a finding that quotes graphql-java ({@code client-schema-invalid} and {@code client-document-invalid})
 * is the text of that library, which changes from one release to the next, so only the code, the member and the lines after the
 * first one are compared for those two.
 */
class ClientErrorReferenceSpec extends Specification {

    static final Path PAGE = Path.of(System.getProperty('guide.errors', '../src/main/docs/guide/exceptions.adoc'))

    static final Set<String> QUOTES_GRAPHQL_JAVA = ['client-schema-invalid', 'client-document-invalid'] as Set

    @Shared
    Map<String, String> examples = readExamples()

    @Unroll
    void "the example of the scenario #scenario is the message of the compile error"() {
        given:
        String code = ClientCodeScenarios.CODES[scenario]
        String actual = ClientCodeScenarios.message(scenario)

        expect:
        examples.containsKey(scenario)
        QUOTES_GRAPHQL_JAVA.contains(code) ? shape(actual) == shape(examples[scenario]) : sizeless(actual) == sizeless(examples[scenario])

        where:
        scenario << ClientCodeScenarios.CODES.keySet()
    }

    void "the page has an example for each scenario of the client harness, and none for another"() {
        expect:
        examples.keySet() == ClientCodeScenarios.CODES.keySet()
    }

    void "the scenarios raise the client codes of the registry, each once"() {
        expect:
        ClientCodeScenarios.CODES.values().toSet() == io.micronaut.graphql.tools.binding.BindingCode.values()
                .findAll { it.code.startsWith('client-') }*.code.toSet()
    }

    // the size of a printed document depends on the printer of the graphql-java release that the leg builds against
    private static String sizeless(String message) {
        return message.replaceAll(/\d+ bytes, and/, 'N bytes, and')
    }

    // the first line up to the member, the lines after it with the directory of the run and the position of the node
    private static String shape(String message) {
        List<String> lines = message.readLines()
        return ([lines.first().replaceFirst(/^(\[[a-z-]+\] [^:]+(:\d*)?(\(.*?\))?): .*$/, '$1')] + lines.drop(1)).join('\n')
    }

    private static Map<String, String> readExamples() {
        List<String> lines = Files.readAllLines(PAGE)
        Map<String, String> found = new LinkedHashMap<>()
        for (int i = 0; i < lines.size(); i++) {
            if (lines[i].startsWith('// client: ')) {
                int open = (i + 1..<lines.size()).find { lines[it] == '----' }
                int close = (open + 1..<lines.size()).find { lines[it] == '----' }
                found.put(lines[i].substring('// client: '.length()).trim(), lines.subList(open + 1, close)*.stripTrailing().join('\n'))
            }
        }
        return found
    }
}
