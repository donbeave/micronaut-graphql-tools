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
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * The directory of the client corpus: every directory below the root that holds {@code case.properties} is a case, named by its
 * relative path; a directory with no case below it, and any file other than the README, is an orphan and a problem.
 */
@CompileStatic
final class ClientCorpus {

    final Path root
    final List<ClientCorpusCase> cases
    final List<String> problems = []

    ClientCorpus(Path root) {
        this.root = root
        List<ClientCorpusCase> found = []
        discover(root, found)
        this.cases = Collections.unmodifiableList(found)
        found.each { c -> c.problems.each { problems.add("${c.name}: ${it}".toString()) } }
    }

    List<List<Object>> units() {
        return cases.collectMany { c -> c.languages().collect { language -> [c, language] as List<Object> } }
    }

    private boolean discover(Path directory, List<ClientCorpusCase> found) {
        if (Files.exists(directory.resolve(ClientCorpusCase.PROPERTIES))) {
            found.add(ClientCorpusCase.load(root, directory))
            return true
        }
        boolean any = false
        list(directory).each { Path entry ->
            if (Files.isDirectory(entry)) {
                any = discover(entry, found) || any
            } else if (!(directory == root && entry.fileName.toString() == 'README.md')) {
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

}
