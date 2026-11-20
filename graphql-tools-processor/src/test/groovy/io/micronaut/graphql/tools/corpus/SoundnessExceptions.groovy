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
 * The documented exceptions to the soundness rule: a compile error that the startup does not report with the same code
 * and coordinate, because the startup fails on something else first or does not bind the method at all. A line is
 * {@code <case> <java|groovy|kotlin|all> <code> <coordinate> <reason>}, and the reason is one of the {@link #REASONS}. The
 * list is per case and per finding: an exception never covers another finding of the case, and an entry that is not needed
 * any more (the startup reports the finding, or the compile side does not) is stale and fails. The header
 * {@code max-entries: N} must equal the number of entries, so a new exception shows as a raised ceiling in the review.
 *
 * <p>This is not the divergence list: a divergence is a startup finding that the compile time misses (completeness), an exception
 * is a compile error that the startup does not show (soundness). Neither list excuses an error that has no reason here.</p>
 */
@CompileStatic
final class SoundnessExceptions {

    static final String FILE = 'corpus-soundness-exceptions.txt'

    /** the reasons, each the sentence of the soundness rule that stops holding */
    static final Map<String, String> REASONS = [
            'two-classes-one-root-field'  : 'two resolver classes bind one root field: the startup reports the ambiguity and judges no argument of either',
            'customizer-claimed-field'    : 'a RuntimeWiringCustomizer claims the field: the startup binds no method to it and judges none of its arguments',
            'runtime-exception-first'     : 'binding the field throws before the argument rule runs (a resolver bean that cannot be created): the startup reports that exception',
            'other-class-binding-failure' : 'a binding failure of another resolver class stops the startup before the strict checks of this class run',
            'conflicting-class-mapping'   : 'two resolver classes bind one input type or enum to different Java classes: the startup reports the conflict before the strict checks',
            'dictionary-entry-wins'       : 'a SchemaMappingDictionary entry names the class of an object type that two classes also match by name: the startup lets the entry win, and the compile-time check cannot see the dictionary',
    ].asImmutable()

    static final class Entry {
        final String caseName
        final String language
        final String code
        final String coordinate
        final String reason

        Entry(String caseName, String language, String code, String coordinate, String reason) {
            this.caseName = caseName
            this.language = language
            this.code = code
            this.coordinate = coordinate
            this.reason = reason
        }

        boolean covers(String name, Language lang, Finding finding) {
            return caseName == name && (language == 'all' || language == lang.dir) && code == finding.code &&
                    coordinate == finding.coordinate
        }

        @Override
        String toString() {
            return "${caseName} ${language} ${code} ${coordinate} ${reason}".toString()
        }
    }

    static final SoundnessExceptions NONE = new SoundnessExceptions(0, [])

    final int ceiling
    final List<Entry> entries
    final List<String> problems

    SoundnessExceptions(int ceiling, List<Entry> entries, List<String> problems = []) {
        this.ceiling = ceiling
        this.entries = entries
        this.problems = problems
    }

    static SoundnessExceptions load(Path file) {
        if (!Files.exists(file)) {
            return new SoundnessExceptions(0, [], ["missing ${FILE} (start it with 'max-entries: 0')".toString()])
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
            if (parts.size() != 5 || !(parts[1] in ['all'] + Language.values()*.dir)) {
                problems.add("${FILE}:${index + 1}: expected '<case> <java|groovy|kotlin|all> <code> <coordinate> <reason>'".toString())
                return
            }
            if (!REASONS.containsKey(parts[4])) {
                problems.add("${FILE}:${index + 1}: unknown reason '${parts[4]}' (known: ${REASONS.keySet().join(', ')})".toString())
                return
            }
            Entry entry = new Entry(parts[0], parts[1], parts[2], parts[3], parts[4])
            if (entries.any { it.toString() == entry.toString() }) {
                problems.add("${FILE}:${index + 1}: '${entry}' is listed twice".toString())
                return
            }
            entries.add(entry)
        }
        if (ceiling < 0) {
            problems.add("${FILE}: the header 'max-entries: N' is missing".toString())
        } else if (entries.size() > ceiling) {
            problems.add("${FILE}: ${entries.size()} entries exceed the ceiling max-entries: ${ceiling}; a new exception needs a reason in the review".toString())
        } else if (entries.size() < ceiling) {
            problems.add("${FILE}: ${entries.size()} entries are below the ceiling max-entries: ${ceiling}; lower the ceiling to ${entries.size()}".toString())
        }
        return new SoundnessExceptions(ceiling, entries, problems)
    }

    /**
     * @return the problems of the entries against the recorded expectations: an unknown case, an entry that is stale (the compile
     * side does not report the error, or the startup reports it)
     */
    List<String> verify(Corpus corpus) {
        List<String> result = []
        entries.each { Entry entry ->
            CorpusCase c = corpus.byName(entry.caseName)
            if (c == null) {
                result.add("${FILE}: '${entry}' names no case".toString())
                return
            }
            List<Language> languages = entry.language == 'all' ? c.languages() : c.languages().findAll { it.dir == entry.language }
            if (languages.empty) {
                result.add("${FILE}: '${entry}': the case has no such language".toString())
            }
            languages.each { Language language ->
                Verdict runtime = c.expectedVerdict(language, 'runtime.txt')
                Verdict compile = c.expectedVerdict(language, 'compile.txt')
                if (compile == null || !compile.errors.any { entry.covers(c.name, language, it) }) {
                    result.add("${FILE}: '${entry}' is stale: ${language.dir}/compile.txt has no such error".toString())
                } else if (runtime != null && runtime.keys().contains(entry.code + ' ' + entry.coordinate)) {
                    result.add("${FILE}: '${entry}' is stale: ${language.dir}/runtime.txt reports it".toString())
                }
            }
        }
        return result
    }

    boolean covers(String caseName, Language language, Finding finding) {
        return entries.any { it.covers(caseName, language, finding) }
    }

}
