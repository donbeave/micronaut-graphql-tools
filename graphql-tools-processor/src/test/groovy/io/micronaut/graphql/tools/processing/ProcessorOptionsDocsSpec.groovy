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
package io.micronaut.graphql.tools.processing

import io.micronaut.graphql.tools.visitor.GraphQLToolsVisitor
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

class ProcessorOptionsDocsSpec extends Specification {

    static final String PAGE = 'src/main/docs/guide/compileTime/options.adoc'
    static final Pattern ENTRY = Pattern.compile('^processor-option:(\\S+)\\s+#\\s+(.+\\(#\\d+\\))$')

    @Shared Path page = Paths.get(System.getProperty('docs.root')).resolve(PAGE)
    @Shared String pageText = Files.isRegularFile(page) ? Files.readString(page) : ''
    @Shared Set<String> allowlist = readAllowlist()

    def "every processor option is documented"() {
        given:
        def options = allOptions().sort()
        def missing = options.findAll { !allowlist.contains(it) && !isDocumented(it) }

        expect:
        options.size() >= 9
        assert missing.empty : "The processor ${missing.size() == 1 ? 'option is' : 'options are'} not documented: " +
                "${missing.join(', ')}. Mention ${missing.size() == 1 ? 'it' : 'each'} in ${PAGE}. " +
                "Only a deliberate decision with a tracker issue allows a gap: add it to docs-coverage-allowlist.txt."
    }

    def "the allowlist has only live entries"() {
        given:
        def options = allOptions()
        def stale = allowlist.findAll { !options.contains(it) || isDocumented(it) }

        expect:
        assert stale.empty : "Stale entries in docs-coverage-allowlist.txt (no such option, or documented now; delete them): " +
                stale.sort().join(', ')
    }

    // the options of the JSR-269 processor, and the two of the visitor that replaces it (#291)
    private static Set<String> allOptions() {
        new GraphQLSchemaBindingProcessor().supportedOptions + new GraphQLToolsVisitor().supportedOptions
    }

    private boolean isDocumented(String option) {
        Pattern.compile("(?<![\\w.-])${Pattern.quote(option)}(?![\\w-]|\\.[\\w-])").matcher(pageText).find()
    }

    private Set<String> readAllowlist() {
        def lines = getClass().getResourceAsStream('/docs-coverage-allowlist.txt').getText('UTF-8').readLines()
        lines.findAll { it.trim() && !it.startsWith('#') }.collect { line ->
            def m = ENTRY.matcher(line)
            assert m.matches() : "Bad allowlist line (want 'processor-option:name # reason (#issue)'): ${line}"
            m.group(1)
        } as Set
    }
}
