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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * A directory of cases, the known-divergence list and the list of soundness exceptions. Every directory below the root that holds
 * {@code case.properties} is a case (its name is the relative path); a directory with no case below it, and any file
 * that is not {@code corpus-divergences.txt}, is an orphan and a problem.
 */
@CompileStatic
final class Corpus {

    static final String DIVERGENCES = 'corpus-divergences.txt'
    /** where the cases of the JSR-269 processor specs went, see {@code PortedCasesSpec} */
    static final String LEDGER = 'processor-cases.txt'

    final Path root
    final List<CorpusCase> cases
    final Divergences divergences
    final SoundnessExceptions exceptions
    final LanguageDifferences differences
    final List<String> problems = []

    Corpus(Path root) {
        this.root = root
        List<CorpusCase> found = []
        discover(root, found)
        this.cases = Collections.unmodifiableList(found)
        this.divergences = Divergences.load(root.resolve(DIVERGENCES))
        this.exceptions = SoundnessExceptions.load(root.resolve(SoundnessExceptions.FILE))
        this.differences = LanguageDifferences.load(root.resolve(LanguageDifferences.FILE))
        found.each { c -> c.problems.each { problems.add("${c.name}: ${it}".toString()) } }
        problems.addAll(divergences.problems)
        problems.addAll(divergences.verify(this))
        problems.addAll(exceptions.problems)
        problems.addAll(exceptions.verify(this))
        problems.addAll(differences.problems)
        problems.addAll(differences.verify(this))
        problems.addAll(Parity.checkExpected(this))
    }

    CorpusCase byName(String name) {
        return cases.find { it.name == name }
    }

    /** @return each case with each of its languages: one unit of work for the runner */
    List<List<Object>> units() {
        return cases.collectMany { c -> c.languages().collect { language -> [c, language] as List<Object> } }
    }

    private boolean discover(Path directory, List<CorpusCase> found) {
        if (Files.exists(directory.resolve(CorpusCase.PROPERTIES))) {
            found.add(CorpusCase.load(root, directory))
            return true
        }
        boolean any = false
        list(directory).each { Path entry ->
            if (Files.isDirectory(entry)) {
                any = discover(entry, found) || any
            } else if (directory == root ? !(entry.fileName.toString() in [DIVERGENCES, SoundnessExceptions.FILE, LanguageDifferences.FILE, LEDGER, 'README.md']) : true) {
                problems.add("orphan file ${root.relativize(entry)}".toString())
            }
        }
        if (!any && directory != root) {
            problems.add("orphan directory ${root.relativize(directory)}: no case below it".toString())
        }
        return any
    }

    private static List<Path> list(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.sorted().collect(Collectors.toList())
        }
    }

    static String read(Path file) {
        return Files.readString(file, StandardCharsets.UTF_8)
    }

}
