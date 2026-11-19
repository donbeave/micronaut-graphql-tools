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
 * The known completeness gaps: a startup finding of a per-class code that the compile-time checks do not report,
 * on purpose or until a fix. A line is {@code <case> <java|groovy|kotlin|all> <code> <coordinate>}. The header
 * {@code max-entries: N} is the ceiling: it must equal the number of entries, so a removed entry forces the ceiling
 * down, and a new entry shows as a raised ceiling in the review. An entry that no longer diverges (the finding is gone
 * from {@code runtime.txt}, or {@code compile.txt} reports it) is stale and fails. The list may only shrink; a false
 * compile error is a bug (soundness) and never goes here.
 */
@CompileStatic
final class Divergences {

    static final class Entry {
        final String caseName
        final String language
        final String code
        final String coordinate

        Entry(String caseName, String language, String code, String coordinate) {
            this.caseName = caseName
            this.language = language
            this.code = code
            this.coordinate = coordinate
        }

        boolean covers(String name, Language lang, Finding finding) {
            return caseName == name && (language == 'all' || language == lang.dir) && code == finding.code &&
                    coordinate == finding.coordinate
        }

        @Override
        String toString() {
            return "${caseName} ${language} ${code} ${coordinate}".toString()
        }
    }

    final int ceiling
    final List<Entry> entries
    final List<String> problems

    Divergences(int ceiling, List<Entry> entries, List<String> problems = []) {
        this.ceiling = ceiling
        this.entries = entries
        this.problems = problems
    }

    static Divergences load(Path file) {
        if (!Files.exists(file)) {
            return new Divergences(0, [], ["missing ${Corpus.DIVERGENCES} (start it with 'max-entries: 0')".toString()])
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
            List<String> parts = line.split(/\s+/).toList()
            if (parts.size() != 4 || !(parts[1] in ['all'] + Language.values()*.dir)) {
                problems.add("${Corpus.DIVERGENCES}:${index + 1}: expected '<case> <java|groovy|kotlin|all> <code> <coordinate>'".toString())
                return
            }
            Entry entry = new Entry(parts[0], parts[1], parts[2], parts[3])
            if (entries.any { it.toString() == entry.toString() }) {
                problems.add("${Corpus.DIVERGENCES}:${index + 1}: '${entry}' is listed twice".toString())
                return
            }
            entries.add(entry)
        }
        if (ceiling < 0) {
            problems.add("${Corpus.DIVERGENCES}: the header 'max-entries: N' is missing".toString())
        } else if (entries.size() > ceiling) {
            problems.add("${Corpus.DIVERGENCES}: ${entries.size()} entries exceed the ceiling max-entries: ${ceiling}; the list may only shrink".toString())
        } else if (entries.size() < ceiling) {
            problems.add("${Corpus.DIVERGENCES}: ${entries.size()} entries are below the ceiling max-entries: ${ceiling}; lower the ceiling to ${entries.size()}".toString())
        }
        return new Divergences(ceiling, entries, problems)
    }

    /** @return the problems of the entries against the recorded expectations: an unknown case, a stale entry */
    List<String> verify(Corpus corpus) {
        List<String> result = []
        entries.each { Entry entry ->
            CorpusCase c = corpus.byName(entry.caseName)
            if (c == null) {
                result.add("${Corpus.DIVERGENCES}: '${entry}' names no case".toString())
                return
            }
            List<Language> languages = entry.language == 'all' ? c.languages() : c.languages().findAll { it.dir == entry.language }
            if (languages.empty) {
                result.add("${Corpus.DIVERGENCES}: '${entry}': the case has no such language".toString())
            }
            languages.each { Language language ->
                Verdict runtime = c.expectedVerdict(language, 'runtime.txt')
                Verdict compile = c.expectedVerdict(language, 'compile.txt')
                if (runtime == null || !runtime.findings.any { entry.covers(c.name, language, it) }) {
                    result.add("${Corpus.DIVERGENCES}: '${entry}' is stale: ${language.dir}/runtime.txt has no such finding".toString())
                } else if (compile != null && compile.findings.any { it.code == entry.code && it.coordinate == entry.coordinate }) {
                    result.add("${Corpus.DIVERGENCES}: '${entry}' is stale: ${language.dir}/compile.txt reports it".toString())
                }
            }
        }
        return result
    }

    boolean covers(String caseName, Language language, Finding finding) {
        return entries.any { it.covers(caseName, language, finding) }
    }

}
