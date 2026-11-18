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

import java.nio.file.Path

/**
 * Where the corpus is and how the Gradle build switches the golden update on ({@code -Pcorpus.update}, which the
 * {@code corpusUpdate} task passes as the system property {@code corpus.update}).
 */
@CompileStatic
final class CorpusFixtures {

    private CorpusFixtures() {
    }

    static Path root() {
        return Path.of(System.getProperty('corpus.dir', 'src/test/resources/corpus')).toAbsolutePath()
    }

    static boolean update() {
        return Boolean.getBoolean('corpus.update')
    }

    /** a regular expression for the names of the cases to run, or {@code null} for all of them (a local aid) */
    static String only() {
        return System.getProperty('corpus.only')
    }

    /** the per-case timings of the last run */
    static Path reportFile() {
        return Path.of(System.getProperty('corpus.report', 'build/corpus/timings.txt')).toAbsolutePath()
    }

    static CorpusRunner runner() {
        return new CorpusRunner(new Corpus(root()), [new JavaCaseCompiler(), new GroovyCaseCompiler()],
                new RuntimeBinder(), new VisitorCompileTimeBinder(), update())
    }

}
