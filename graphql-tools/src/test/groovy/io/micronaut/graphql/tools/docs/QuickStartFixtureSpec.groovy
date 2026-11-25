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
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * The quick start of the guide takes its code from the docs-examples-* modules: the three modules hold the same
 * classes with the same tags, and the snippets of the page resolve to them.
 */
class QuickStartFixtureSpec extends Specification {

    static final Map<String, String> LANGUAGES = [java: 'java', kotlin: 'kt', groovy: 'groovy']

    @Shared Path root = Paths.get(System.getProperty('docs.root'))
    @Shared String page = Files.readString(root.resolve('src/main/docs/guide/quickStart.adoc'))

    private Map<String, Set<String>> tagsByClass(String language, String source) {
        Path dir = root.resolve("docs-examples-${language}/src/${source}/${language}/example/quickstart")
        if (!Files.isDirectory(dir)) {
            return [:]
        }
        List<Path> files = Files.list(dir).withCloseable { Stream<Path> s -> s.collect(Collectors.toList()) }
        files.findAll { Files.isRegularFile(it) }.collectEntries { Path file ->
            [(file.fileName.toString().replaceFirst(/\.[^.]+$/, '')):
                     (Files.readString(file) =~ /tag::([\w-]+)\[]/).collect { it[1] } as Set<String>]
        }
    }

    def "the three fixtures hold the same classes with the same tags"() {
        given:
        def main = LANGUAGES.keySet().collect { tagsByClass(it, 'main') }
        def test = LANGUAGES.keySet().collect { tagsByClass(it, 'test') }

        expect:
        main[0].keySet() == ['AllBooksQuery', 'Book', 'BookRepository', 'GraphQLFactory'] as Set
        main.every { it == main[0] }
        test[0].keySet() == ['QuickStartTest'] as Set
        test.every { it == test[0] }
    }

    def "every snippet of the quick start names a class and a tag of the fixtures"() {
        given:
        def snippets = (page =~ /snippet::([\w.]+)\[([^\]]*)]/).collect { [it[1], it[2]] }
        def main = tagsByClass('java', 'main')
        def test = tagsByClass('java', 'test')

        expect:
        !snippets.isEmpty()
        snippets.every { String type, String args ->
            def tags = type.startsWith('example.quickstart.') ?
                    (args.contains('source="test"') ? test : main)[type.substring('example.quickstart.'.length())] : null
            args =~ /(?:^|,\s*)project-base="docs-examples"/ && tags != null && (args =~ /tags="([^"]*)"/)[0][1] in tags
        }
    }
}
