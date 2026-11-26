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
package io.micronaut.graphql.tools.docs

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The breaking change ledger: the page of breaking changes and the migration page number each change once, in the same release,
 * with a summary row and an entry, and the numbers have no gap except the ones that the ledger declares unused.
 */
class BreakingChangeLedgerSpec extends Specification {

    /** A number that was reserved by an issue that made no breaking change. */
    static final Set<Integer> UNUSED = [48] as Set

    /** The numbers of 1.0.0-M1 are listed as one range on the page of breaking changes, so they are not checked one by one. */
    static final int LAST_RANGED = 19

    @Shared Path root = Paths.get(System.getProperty('docs.root'))
    @Shared String breaks = Files.readString(root.resolve('src/main/docs/guide/breaks.adoc'))
    @Shared String migration = Files.readString(root.resolve('src/main/docs/guide/migration/fromSnapshot.adoc'))

    def "every number from BC-20 on is listed once on the page of breaking changes, and the numbers have no gap"() {
        given:
        def listed = breaks.readLines().findAll { it.startsWith('* #') }.collect { (it =~ /^\* #\d+ \(BC-(\d+)\)/) }
                .findAll { it.find() }.collect { it.group(1) as int }
        def duplicates = listed.findAll { n -> listed.count(n) > 1 }.unique()
        def max = listed.max()

        expect:
        assert duplicates.empty: "Listed twice on the page of breaking changes: ${duplicates.collect { 'BC-' + it }}"
        assert listed.min() == LAST_RANGED + 1: 'The first listed number must follow the range of 1.0.0-M1'
        def gaps = ((LAST_RANGED + 1)..max).findAll { !listed.contains(it) && !UNUSED.contains(it) }
        assert gaps.empty: "Numbers missing from the page of breaking changes (use the next number, or declare the gap in UNUSED): ${gaps.collect { 'BC-' + it }}"
        assert UNUSED.every { !listed.contains(it) }
    }

    def "every change has a summary row and an entry on the migration page, under the release of the page of breaking changes"() {
        given:
        def problems = []
        def released = releaseOfBreaks()
        def entries = releaseOfMigration()
        def rows = migration.readLines().findAll { it.startsWith('| BC-') }.collect { (it =~ /^\| BC-(\d+) /)[0][1] as int }
        released.each { number, release ->
            if (!entries.containsKey(number)) {
                problems << "BC-${number}: no entry on the migration page"
            } else if (entries[number] != release) {
                problems << "BC-${number}: listed under ${release} on the page of breaking changes, and under ${entries[number]} on the migration page"
            }
            if (!rows.contains(number)) {
                problems << "BC-${number}: no row in the summary table of the migration page"
            }
        }
        entries.keySet().findAll { it > LAST_RANGED && !released.containsKey(it) }.each {
            problems << "BC-${it}: an entry on the migration page that the page of breaking changes does not list"
        }

        expect:
        released.size() >= 30
        assert problems.empty: "Ledger gaps:\n  " + problems.join('\n  ')
    }

    def "the migration page has no section of changes that are not on master"() {
        expect:
        !migration.readLines().any { it =~ /^== Pending changes/ }
    }

    private Map<Integer, String> releaseOfBreaks() {
        Map<Integer, String> result = [:]
        String release = null
        breaks.readLines().each { line ->
            def heading = line =~ /^={2,3} (1\.0\.0-\S+)$/
            if (heading.find()) {
                release = heading.group(1)
            }
            def entry = line =~ /^\* #\d+ \(BC-(\d+)\)/
            if (release != null && entry.find()) {
                result[entry.group(1) as int] = release
            }
        }
        result
    }

    private Map<Integer, String> releaseOfMigration() {
        Map<Integer, String> result = [:]
        String release = null
        migration.readLines().each { line ->
            def heading = line =~ /^== Entries for (1\.0\.0-\S+)$/
            if (heading.find()) {
                release = heading.group(1)
            } else if (line =~ /^== /) {
                release = null
            }
            def entry = line =~ /^=== BC-(\d+):/
            if (release != null && entry.find()) {
                result[entry.group(1) as int] = release
            }
        }
        result
    }

}
