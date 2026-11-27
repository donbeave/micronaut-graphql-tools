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
package io.micronaut.graphql.tools.codegen

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Every component of Options is documented with its default, and the string form covers every component.
 */
class OptionsDocumentationSpec extends Specification {

    def "every component has a @param entry that states its default"() {
        given:
        def source = Files.readString(Paths.get(System.getProperty('codegen.sources')).resolve('Options.java'))
        def javadoc = source.substring(source.indexOf('/**\n * The options of the generator'), source.indexOf('public record Options('))
        def entries = javadoc.split('(?m)^ \\* @param ').toList().drop(1).collectEntries {
            def text = it.replaceAll('(?m)^ \\*\\s+', ' ').replaceAll('\\s+', ' ').trim()
            [(text.split(' ')[0]): text]
        }

        expect:
        Options.getRecordComponents()*.name.every { name ->
            entries[name] != null && (entries[name].contains('Default') || entries[name].contains('default'))
        }
        entries.keySet() == Options.getRecordComponents()*.name as Set
    }

    def "every component has a key in the string form of a changed value"() {
        given:
        def map = OptionsSpec.everything().toMap() + Options.builder().mode(Mode.CLIENT).build().toMap()
        def names = map.keySet().collect { it.replaceFirst('\\..*', '') } as Set

        expect:
        names == Options.getRecordComponents()*.name as Set
    }

    def "the README option table names every component"() {
        given:
        def readme = Files.readString(Paths.get(System.getProperty('codegen.readme')))
        def table = readme.substring(readme.indexOf('| Option |'), readme.indexOf('An unmapped custom scalar'))

        expect:
        Options.getRecordComponents()*.name.every { table.contains("`${it}`") }
    }
}
