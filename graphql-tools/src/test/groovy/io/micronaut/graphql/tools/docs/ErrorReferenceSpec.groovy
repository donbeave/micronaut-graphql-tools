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
package io.micronaut.graphql.tools.docs

import io.micronaut.graphql.tools.binding.BindingCode
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The code tables of the error reference are generated from {@link BindingCode}: a code, its kind, whether the compile-time
 * check reports it and its summary. Run the build with UPDATE_ERROR_REFERENCE=true to rewrite them after a change of the enum.
 */
class ErrorReferenceSpec extends Specification {

    static final String PAGE = 'src/main/docs/guide/exceptions.adoc'

    @Shared Path page = Paths.get(System.getProperty('docs.root')).resolve(PAGE)

    def "the tables of the error reference are the codes of the registry, in order, with their summaries"() {
        given:
        String text = Files.readString(page)
        String updated = text
        ['checks': true, 'failures': false].each { name, check ->
            updated = replaceBetween(updated, name, rows(check))
        }
        boolean update = System.getenv('UPDATE_ERROR_REFERENCE') == 'true'
        if (update && updated != text) {
            Files.writeString(page, updated)
        }

        expect:
        assert update || updated == text: "The tables of ${PAGE} differ from BindingCode. Run the build with UPDATE_ERROR_REFERENCE=true to regenerate them."
    }

    def "every code is in exactly one row"() {
        given:
        def codes = Files.readAllLines(page).findAll { it.startsWith('|`') }.collect { it.split(' \\|')[0].substring(2).replace('`', '') }

        expect:
        codes.sort() == BindingCode.values()*.getCode().sort()
    }

    private static String rows(boolean check) {
        BindingCode.values().findAll { it.isCheck() == check }.collect {
            "|`${it.getCode()}` |${it.isCheck() ? 'check' : 'failure'} |${it.isCompileTime() ? 'yes' : 'no'} |${it.getSummary()}"
        }.join('\n')
    }

    private static String replaceBetween(String text, String name, String rows) {
        String begin = "// begin generated: ${name}\n"
        String end = "// end generated: ${name}\n"
        int from = text.indexOf(begin)
        int to = text.indexOf(end)
        assert from >= 0 && to > from: "${PAGE} has no generated section ${name}"
        text.substring(0, from + begin.length()) + rows + '\n' + text.substring(to)
    }
}
