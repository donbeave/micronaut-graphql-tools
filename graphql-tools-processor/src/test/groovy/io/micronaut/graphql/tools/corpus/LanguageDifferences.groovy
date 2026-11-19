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

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

/**
 * The languages of a case share its expected files: the same fixture fails with the same code and coordinate under javac, KSP and
 * Groovy. A file under {@code expected/<lang>/} says that one language differs, and it is allowed only with a line here:
 * {@code <case> <java|groovy|kotlin> <runtime.txt|compile.txt|manifest.txt> <reason...>}. The header {@code max-entries: N} must equal
 * the number of entries, so the list can only shrink, and a new difference shows as a raised ceiling in the review. An entry whose
 * override is gone is stale and fails, and so does an override without an entry.
 */
@CompileStatic
final class LanguageDifferences {

    static final String FILE = 'corpus-language-differences.txt'

    static final class Entry {
        final String caseName
        final Language language
        final String file
        final String reason

        Entry(String caseName, Language language, String file, String reason) {
            this.caseName = caseName
            this.language = language
            this.file = file
            this.reason = reason
        }

        @Override
        String toString() {
            return "${caseName} ${language.dir} ${file} ${reason}".toString()
        }
    }

    final int ceiling
    final List<Entry> entries
    final List<String> problems

    LanguageDifferences(int ceiling, List<Entry> entries, List<String> problems = []) {
        this.ceiling = ceiling
        this.entries = entries
        this.problems = problems
    }

    static LanguageDifferences load(Path file) {
        if (!Files.exists(file)) {
            return new LanguageDifferences(0, [], ["missing ${FILE} (start it with 'max-entries: 0')".toString()])
        }
        int ceiling = -1
        List<Entry> entries = []
        List<String> problems = []
        Files.readAllLines(file).eachWithIndex { String raw, int index ->
            String line = raw.trim()
            if (line.empty || line.startsWith('#')) {
                return
            }
            if (line.startsWith('max-entries:')) {
                ceiling = Integer.parseInt(line.substring('max-entries:'.length()).trim())
                return
            }
            List<String> parts = line.split(/\s+/, 4).toList()
            Language language = parts.size() == 4 ? Language.values().find { it.dir == parts[1] } : null
            if (language == null || !(parts[2] in CorpusCase.EXPECTED_FILES)) {
                problems.add("${FILE}:${index + 1}: expected '<case> <java|groovy|kotlin> <${CorpusCase.EXPECTED_FILES.join('|')}> <reason>'".toString())
                return
            }
            Entry entry = new Entry(parts[0], language, parts[2], parts[3])
            if (entries.any { it.caseName == entry.caseName && it.language == language && it.file == entry.file }) {
                problems.add("${FILE}:${index + 1}: '${parts[0]} ${parts[1]} ${parts[2]}' is listed twice".toString())
                return
            }
            entries.add(entry)
        }
        if (ceiling < 0) {
            problems.add("${FILE}: the header 'max-entries: N' is missing".toString())
        } else if (entries.size() > ceiling) {
            problems.add("${FILE}: ${entries.size()} entries exceed the ceiling max-entries: ${ceiling}; the list may only shrink".toString())
        } else if (entries.size() < ceiling) {
            problems.add("${FILE}: ${entries.size()} entries are below the ceiling max-entries: ${ceiling}; lower the ceiling to ${entries.size()}".toString())
        }
        return new LanguageDifferences(ceiling, entries, problems)
    }

    /** @return the problems of the entries against the override files: a stale entry, an override with no entry */
    List<String> verify(Corpus corpus) {
        List<String> result = []
        entries.each { Entry entry ->
            CorpusCase c = corpus.byName(entry.caseName)
            if (c == null) {
                result.add("${FILE}: '${entry}' names no case".toString())
            } else if (!c.hasOwnExpected(entry.language, entry.file)) {
                result.add("${FILE}: '${entry}' is stale: there is no expected/${entry.language.dir}/${entry.file}".toString())
            }
        }
        corpus.cases.each { CorpusCase c ->
            c.languages().each { Language language ->
                CorpusCase.EXPECTED_FILES.findAll { c.hasOwnExpected(language, it) }.each { String file ->
                    if (!entries.any { it.caseName == c.name && it.language == language && it.file == file }) {
                        result.add("${c.name}: expected/${language.dir}/${file} makes ${language.dir} differ from the other languages: list it in ${FILE} with a reason, or fix the language that is wrong (run corpusUpdate and corpusKotlin -Pcorpus.update)".toString())
                    }
                }
            }
        }
        return result
    }

}
