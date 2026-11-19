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

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher

/**
 * Every test method of the JSR-269 processor specs has a line in {@code processor-cases.txt}: the corpus cases it went to, or the
 * reason it was deleted. A method without a line, a line without a method (stale) and a case that does not exist fail. The
 * specs and the ledger are deleted together when the JSR-269 code is.
 */
class PortedCasesSpec extends Specification {

    static Path specs() {
        return Path.of(System.getProperty('processing.specs', 'src/test/groovy/io/micronaut/graphql/tools/processing')).toAbsolutePath()
    }

    /** @return the {@code spec TAB method} of every test method of the JSR-269 specs */
    static List<String> methods() {
        List<String> result = []
        Files.list(specs()).sorted().withCloseable { files ->
            files.filter { it.fileName.toString().endsWith('Spec.groovy') }.each { Path file ->
                String spec = file.fileName.toString() - '.groovy'
                Matcher matcher = (Files.readString(file) =~ /(?m)^\s+void "(.*)"\(\) \{/)
                while (matcher.find()) {
                    result.add(spec + '\t' + matcher.group(1))
                }
            }
        }
        return result
    }

    static List<List<String>> ledger() {
        return Files.readAllLines(CorpusFixtures.root().resolve(Corpus.LEDGER)).findAll { !it.startsWith('#') && !it.trim().empty }
                .collect { it.split('\t', -1).toList() }
    }

    void "every test method of the JSR-269 specs is a corpus case or is deleted with a reason"() {
        given:
        List<String> methods = methods()
        List<List<String>> lines = ledger()
        List<String> listed = lines.collect { it[0] + '\t' + it[1] }

        expect:
        !methods.empty
        lines.every { it.size() == 4 }
        methods.findAll { !(it in listed) } == []
        listed.findAll { !(it in methods) } == []
        listed.unique(false).size() == listed.size()
    }

    void "a line names corpus cases that exist, or starts its note with 'deleted:' and a reason"() {
        given:
        Corpus corpus = new Corpus(CorpusFixtures.root())

        expect:
        ledger().every { List<String> line ->
            List<String> cases = line[2] == '-' ? [] : line[2].split(',').toList()
            (cases.empty ? line[3].startsWith('deleted: ') && line[3].length() > 'deleted: '.length() : true) &&
                    cases.every { corpus.byName(it) != null }
        }
    }

    void "the ledger counts the cases before and after the move"() {
        given:
        List<List<String>> lines = ledger()
        Set<String> ported = lines.collectMany { it[2] == '-' ? [] : it[2].split(',').toList() } as Set<String>

        expect: 'the numbers that the pull request reports'
        lines.size() == methods().size()
        ported.size() > 0
        lines.count { it[2] == '-' } > 0
    }

}
