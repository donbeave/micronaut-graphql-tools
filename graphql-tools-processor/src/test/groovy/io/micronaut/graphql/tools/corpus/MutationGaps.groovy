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
import java.util.concurrent.ConcurrentHashMap

/**
 * The known gaps of the drift mutation tests (#89): a kind of breaking mutant that the binders let through, on purpose or until a
 * fix. A line is {@code <category> <mutant kind> <java|groovy|all> <reason>}, where the category says what went wrong
 * ({@link #CATEGORIES}). The header {@code max-entries: N} is the ceiling: it must equal the number of entries, so a removed entry
 * forces the ceiling down and a new one shows as a raised ceiling in the review. A mutant that falls under a line is reported as
 * known; a line that no mutant of a full run falls under is stale and fails. The list may only shrink.
 */
@CompileStatic
final class MutationGaps {

    static final String FILE = 'corpus-mutation-gaps.txt'

    /**
     * {@code silent}: the startup binds a breaking mutant; {@code unnamed}: it fails, but no finding names the mutated element;
     * {@code compile-missed}: a finding of a per-class code that the compile side does not report;
     * {@code compile-extra}: a compile error that neither the startup reports nor names the mutated element.
     */
    static final List<String> CATEGORIES = ['silent', 'unnamed', 'compile-missed', 'compile-extra']

    static final class Entry {
        final String category
        final String kind
        final String language
        final String reason

        Entry(String category, String kind, String language, String reason) {
            this.category = category
            this.kind = kind
            this.language = language
            this.reason = reason
        }

        boolean covers(String category, String kind, Language lang) {
            return this.category == category && this.kind == kind && (language == 'all' || language == lang.dir)
        }

        @Override
        String toString() {
            return "${category} ${kind} ${language}".toString()
        }
    }

    final int ceiling
    final List<Entry> entries
    final List<String> problems
    private final Set<String> used = ConcurrentHashMap.newKeySet()

    MutationGaps(int ceiling, List<Entry> entries, List<String> problems = []) {
        this.ceiling = ceiling
        this.entries = entries
        this.problems = problems
    }

    static MutationGaps load(Path file) {
        if (!Files.exists(file)) {
            return new MutationGaps(0, [])
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
            if (parts.size() != 4 || !(parts[0] in CATEGORIES) || !(parts[2] in ['all'] + Language.values()*.dir)) {
                problems.add("${FILE}:${index + 1}: expected '<${CATEGORIES.join('|')}> <mutant kind> <java|groovy|kotlin|all> <reason>'".toString())
                return
            }
            Entry entry = new Entry(parts[0], parts[1], parts[2], parts[3])
            if (entries.any { it.toString() == entry.toString() }) {
                problems.add("${FILE}:${index + 1}: '${entry}' is listed twice".toString())
                return
            }
            entries.add(entry)
        }
        if (ceiling < 0) {
            problems.add("${FILE}: the header 'max-entries: N' is missing".toString())
        } else if (entries.size() != ceiling) {
            problems.add("${FILE}: ${entries.size()} entries and the ceiling max-entries: ${ceiling}; they must be equal, and the list may only shrink".toString())
        }
        return new MutationGaps(ceiling, entries, problems)
    }

    /** @return whether a line excuses the finding, remembering that it was used */
    boolean covers(String category, String kind, Language language) {
        Entry entry = entries.find { it.covers(category, kind, language) }
        if (entry != null) {
            used.add(entry.toString())
        }
        return entry != null
    }

    /** @return the lines that no mutant fell under: after a run of every mutant, each of them is stale */
    List<String> unused() {
        return entries.findAll { !used.contains(it.toString()) }.collect { "${FILE}: '${it}' is stale: no mutant falls under it".toString() }
    }

}
