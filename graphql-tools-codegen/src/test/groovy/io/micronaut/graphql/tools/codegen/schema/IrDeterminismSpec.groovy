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
package io.micronaut.graphql.tools.codegen.schema

import io.micronaut.graphql.tools.codegen.Diagnostic
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.ir.IrSnapshot
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class IrDeterminismSpec extends Specification {

    static final Options OPTIONS = Options.builder().packageName('com.example').typeMappings([Date: 'java.time.LocalDate']).build()
    static final Options PLAIN = Options.builder().packageName('com.example').build()

    static final Options SAMPLE_OPTIONS = OPTIONS.toBuilder().typeMappings([Date: 'java.time.LocalDate', Money: 'java.math.BigDecimal']).build()

    @TempDir Path dir

    def "the same files in any order give an identical IR"() {
        given:
        def sample = Paths.get(IrDeterminismSpec.getResource('/schema/sample').toURI())
        def files = Files.list(sample).filter { it.toString().endsWith('.graphqls') }.toList()
        def expected = IrBuilder.fromFiles(files, SAMPLE_OPTIONS)

        expect:
        !expected.hasErrors()
        expected.ir().models().size() == 3
        permutations(files).every { order ->
            def result = IrBuilder.fromFiles(order, SAMPLE_OPTIONS)
            result.ir() == expected.ir() && IrSnapshot.print(result.ir()) == IrSnapshot.print(expected.ir()) &&
                    result.diagnostics() == expected.diagnostics()
        }
    }

    def "sources in any order, and a directory, give the same IR as the files"() {
        given:
        def sources = [
                new SchemaSource('b/two.graphqls', 'extend type A { y: Int }'),
                new SchemaSource('a/one.graphqls', 'type Query { a: A }\ntype A { x: Int }'),
                new SchemaSource('b/three.graphqls', 'extend type A { z: Int }'),
                new SchemaSource('b/two.graphqls', 'extend type A { y: Int }')]
        sources.take(3).each {
            def file = dir.resolve(it.path())
            Files.createDirectories(file.parent)
            Files.writeString(file, it.text())
        }
        def expected = IrBuilder.fromSources(sources, PLAIN)

        expect:
        expected.diagnostics().empty
        expected.ir().models()[0].fields()*.name() == ['x', 'z', 'y']
        permutations(sources).every { IrBuilder.fromSources(it, PLAIN).ir() == expected.ir() }
        IrBuilder.fromDirectory(dir, PLAIN).ir() == expected.ir()
    }

    def "files are ordered by the UTF-8 bytes of their path, not by the locale or the file system"() {
        given:
        def sources = ['Z.graphqls', 'a.graphqls', '\u00e9.graphqls', 'B.graphqls', '_.graphqls'].collect {
            new SchemaSource(it, "type T${it.codePointAt(0)} { x: Int }".toString())
        } + [new SchemaSource('query.graphqls', 'type Query { q: Int }')]

        when:
        def result = IrBuilder.fromSources(sources.reverse(), PLAIN)

        then:
        result.ir().models()*.names()*.sdlName() == ['T66', 'T90', 'T95', 'T97', 'T233']
    }

    def "a Turkish default locale changes nothing"() {
        given:
        def saved = Locale.default
        Locale.setDefault(new Locale('tr', 'TR'))

        when:
        def result = IrBuilder.fromSources([new SchemaSource('a.graphqls',
                'type Query { info: Int  ilk: Int }\ntype INFO { i: Int }\ntype info { i: Int }')], PLAIN)

        then:
        result.diagnostics()*.code() == [Diagnostic.NAME_COLLISION, Diagnostic.NAME_COLLISION]
        result.diagnostics()[0].message().contains("the object type 'INFO' (a.graphqls:2:1) generates com.example.model.INFO")

        cleanup:
        Locale.setDefault(saved)
    }

    def "a schema of 1000 types and 20000 fields builds the same IR twice"() {
        given:
        def sources = [new SchemaSource('large.graphqls', largeSchema())]

        when:
        def first = IrBuilder.fromSources(sources, OPTIONS)
        def second = IrBuilder.fromSources(sources, OPTIONS)

        then:
        first.diagnostics().empty
        first.ir().models().size() == 699
        first.ir().inputs().size() == 150
        first.ir().enums().size() == 100
        first.ir().abstractTypes().size() == 50
        first.ir().contracts().size() == 620
        first.ir().models().sum { it.fields().size() } + first.ir().inputs().sum { it.fields().size() } + 620 == 20000
        first.ir() == second.ir()
        IrSnapshot.print(first.ir()) == IrSnapshot.print(second.ir())
    }

    static String largeSchema() {
        def sdl = new StringBuilder()
        sdl << 'type Query {\n'
        (0..<620).each { sdl << "  field${it}(first: Int = 10): [Type${it % 699}!]!\n" }
        sdl << '}\n'
        (0..<699).each { type ->
            sdl << "type Type${type} {\n"
            (0..<20).each { sdl << "  f${it}: ${it % 4 == 0 ? 'String' : it % 4 == 1 ? 'Int!' : it % 4 == 2 ? 'Enum' + (type % 100) : 'Date'}\n" }
            sdl << '}\n'
        }
        (0..<150).each { input ->
            sdl << "input Input${input} {\n"
            (0..<36).each { sdl << "  i${it}: ${it % 2 == 0 ? 'Int' : 'String!'}\n" }
            sdl << '}\n'
        }
        (0..<100).each { sdl << "enum Enum${it} { A B C }\n" }
        (0..<50).each { sdl << "union Union${it} = Type${it} | Type${it + 50}\n" }
        sdl << 'scalar Date\n'
        sdl.toString()
    }

    private static List permutations(List items) {
        def result = []
        def random = new Random(7)
        8.times {
            def copy = new ArrayList(items)
            Collections.shuffle(copy, random)
            result << copy
        }
        result << items.reverse()
        result
    }
}
