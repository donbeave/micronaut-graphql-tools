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

import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * The drift mutation tests (#89): the mutants of the SDL of the binding corpus, run on a fixed seed ({@code -Pmutation.seed}, default
 * {@code CorpusFixtures.DEFAULT_MUTATION_SEED}) up to a limit ({@code -Pmutation.limit}, a sample by default and {@code 0} for every
 * mutant: the task {@code corpusMutation}). See {@code DriftMutationRunner} for what a mutant is judged by and the README of the corpus for how to run them.
 */
class DriftMutationSpec extends Specification {

    @Shared
    DriftMutationRunner runner = CorpusFixtures.mutationRunner()

    @Unroll
    void "#unit"() {
        expect:
        runner.run(unit)

        where:
        unit << runner.units()
    }

    @Requires({ CorpusFixtures.mutationLimit() == 0 && CorpusFixtures.only() == null })
    void "every line of the list of known gaps is used by a mutant"() {
        expect:
        runner.staleGaps() == []
    }

    void cleanupSpec() {
        Path report = CorpusFixtures.mutationReportFile()
        Files.createDirectories(report.parent)
        Files.writeString(report, runner.report())
    }

}
