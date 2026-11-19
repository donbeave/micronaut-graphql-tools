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

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

/**
 * Comments, docs and build files do not cite the maintainers' planning notes: no decision id, no design rule id and no
 * path into the planning folder. A reader of the repository cannot resolve those ids, so the reason is written out in
 * place, or an issue or pull request number is given. The spec reads every text file of the project tree.
 */
class NoPlanningReferencesSpec extends Specification {

    /** The forbidden forms. Each one is split in two, so that this file does not contain the text it looks for. */
    static final List<Pattern> FORBIDDEN = [
            Pattern.compile('\\bD-' + '[0-9]{2}\\b'),
            Pattern.compile('\\bDD-' + '[0-9]+'),
            Pattern.compile('opus' + '-design'),
            Pattern.compile('\\.plan' + 'ning/'),
    ]

    /** Not repository content: build output, VCS and Gradle state, and the local notes folders that git excludes. */
    static final Set<String> SKIPPED_DIRECTORIES = ['build', '.git', '.gradle', '.planning', '.claude'] as Set

    @Shared Path root = Paths.get(System.getProperty('docs.root'))

    def "no file of the project tree cites a decision id, a design rule id or a planning file"() {
        given:
        def findings = scan(root)

        expect:
        assert findings.empty: "Planning references (write the reason out, or cite an issue or pull request instead):\n  " +
                findings.join('\n  ')
    }

    def "the scan reports each forbidden form with its file and line"() {
        given:
        def dir = Files.createTempDirectory('planning-references')
        def file = dir.resolve('Notes.java')
        Files.writeString(file, "class Notes {\n// the rule of ${'D-' + '99'} and ${'DD' + '-3'}\n// see ${'.plan' + 'ning/'}x\n}\n")

        when:
        def findings = scan(dir)

        then:
        findings.collect { it.substring(0, it.indexOf(': ')) } == ['Notes.java:2', 'Notes.java:3']

        cleanup:
        Files.delete(file)
        Files.delete(dir)
    }

    static List<String> scan(Path base) {
        def findings = []
        treeFiles(base).each { file ->
            def bytes = Files.readAllBytes(file)
            if ((0..<bytes.length).any { bytes[it] == (byte) 0 }) {
                return
            }
            new String(bytes, StandardCharsets.UTF_8).split('\n', -1).eachWithIndex { line, index ->
                if (FORBIDDEN.any { it.matcher(line).find() }) {
                    findings << "${base.relativize(file)}:${index + 1}: ${line.trim()}".toString()
                }
            }
        }
        findings
    }

    // The files Git tracks or would track, so ignored local content (an IDE folder, a log) is not scanned.
    // Outside a Git work tree, every file below the base that is not in a skipped directory.
    static List<Path> treeFiles(Path base) {
        def git = new ProcessBuilder('git', '-C', base.toString(), 'ls-files', '-z', '--cached', '--others', '--exclude-standard')
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        def listed = git.inputStream.readAllBytes()
        if (git.waitFor() == 0) {
            return new String(listed, StandardCharsets.UTF_8).split('\u0000').collect { base.resolve(it) }
                    .findAll { Files.isRegularFile(it) && !inSkippedDirectory(base, it) }
        }
        def files = []
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                dir != base && SKIPPED_DIRECTORIES.contains(dir.fileName.toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                files << file
                FileVisitResult.CONTINUE
            }
        })
        files
    }

    static boolean inSkippedDirectory(Path base, Path file) {
        def parts = base.relativize(file).collect { it.toString() }
        parts.dropRight(1).any { SKIPPED_DIRECTORIES.contains(it) }
    }
}
