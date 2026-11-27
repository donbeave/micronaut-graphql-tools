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
package io.micronaut.graphql.tools.codegen.golden

import io.micronaut.graphql.tools.codegen.GeneratedFile
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GoldenHarnessSpec extends Specification {

    @TempDir
    Path dir

    static final List<GeneratedFile> JAVA = [
            new GeneratedFile('com/example/Pet.java', 'package com.example;\n\npublic record Pet(String name) {\n}\n'),
            new GeneratedFile('com/example/Owner.java', 'package com.example;\n\npublic record Owner(Pet pet) {\n}\n')
    ]

    static final List<GeneratedFile> GROOVY = [
            new GeneratedFile('com/example/Pet.groovy', 'package com.example\n\n@groovy.transform.CompileStatic\nclass Pet {\n    String name\n}\n')
    ]

    def "the checked-in snapshots of the sample cases match and compile"() {
        expect:
        new GoldenFiles().assertMatches('sample-java', JAVA)
        new GoldenFiles().assertMatches('sample-groovy', GROOVY)
        new JavaInMemoryCompiler().assertCompiles(JAVA)
        new GroovyInMemoryCompiler().assertCompiles(GROOVY)
    }

    def "a changed output fails with a readable diff and an update rewrites the snapshot"() {
        given:
        def golden = new GoldenFiles(dir, false)
        def changed = [new GeneratedFile('A.java', 'class A {\n    int x;\n    int y;\n}\n')]
        new GoldenFiles(dir, true).assertMatches('c', [new GeneratedFile('A.java', 'class A {\n    int x;\n}\n')])

        when:
        golden.assertMatches('c', changed)

        then:
        def e = thrown(AssertionError)
        e.message.contains('-PupdateGolden')
        e.message.contains('Changed file A.java')
        e.message.contains('+     int y;')

        when:
        new GoldenFiles(dir, true).assertMatches('c', changed)
        golden.assertMatches('c', changed)

        then:
        noExceptionThrown()
        Files.readString(dir.resolve('c/A.java')) == changed[0].content()
    }

    def "a missing and an unexpected file fail"() {
        given:
        new GoldenFiles(dir, true).assertMatches('c', [new GeneratedFile('A.java', 'a\n')])

        when:
        new GoldenFiles(dir, false).assertMatches('c', [new GeneratedFile('B.java', 'b\n')])

        then:
        def e = thrown(AssertionError)
        e.message.contains('Missing generated file A.java')
        e.message.contains('Unexpected generated file B.java')
    }

    def "an update removes the snapshot of a file that is not generated any more"() {
        given:
        new GoldenFiles(dir, true).assertMatches('c', [new GeneratedFile('A.java', 'a\n'), new GeneratedFile('p/B.java', 'b\n')])

        when:
        new GoldenFiles(dir, true).assertMatches('c', [new GeneratedFile('A.java', 'a\n')])

        then:
        !Files.exists(dir.resolve('c/p/B.java'))
        Files.exists(dir.resolve('c/A.java'))
    }

    def "an IR text is a snapshot like any other file"() {
        when:
        new GoldenFiles(dir, true).assertText('ir', 'schema.txt', 'type Pet\n')
        new GoldenFiles(dir, false).assertText('ir', 'schema.txt', 'type Pet\n')
        new GoldenFiles(dir, false).assertText('ir', 'schema.txt', 'type Dog\n')

        then:
        thrown(AssertionError)
    }

    def "a deliberately broken snapshot fails the compile check"() {
        given:
        def brokenJava = [new GeneratedFile('com/example/Pet.java', 'package com.example;\npublic record Pet(Strin name) {}\n')]
        def brokenGroovy = [new GeneratedFile('com/example/Pet.groovy', 'package com.example\nclass Pet {\n')]

        when:
        new JavaInMemoryCompiler().assertCompiles(brokenJava)

        then:
        def e = thrown(AssertionError)
        e.message.contains('Pet.java:2')

        when:
        new GroovyInMemoryCompiler().assertCompiles(brokenGroovy)

        then:
        thrown(AssertionError)
    }

    def "the output must be identical across runs and schema orders"() {
        given:
        def files = ['a', 'b', 'c'].collect { Files.writeString(dir.resolve("${it}.graphqls"), it) }

        expect:
        Determinism.assertDeterministic(files) { order -> order.collect { new GeneratedFile(it.fileName.toString() + '.txt', 'x\n') }.sort { it.path() } }

        when:
        Determinism.assertDeterministic(files) { order -> order.collect { new GeneratedFile(it.fileName.toString() + '.txt', 'x\n') } }

        then:
        thrown(AssertionError)

        when:
        def counter = 0
        Determinism.assertDeterministic(files) { order -> [new GeneratedFile('A.txt', "${counter++}\n")] }

        then:
        thrown(AssertionError)
    }

    def "carriage returns, timestamps and a dated @Generated are rejected"() {
        when:
        Determinism.assertContent([new GeneratedFile('A.java', content)])

        then:
        thrown(AssertionError)

        where:
        content << ['a\r\nb\n', '// 2026-01-02T03:04:05Z\n', '@Generated(value = "x", date = "y")\nclass A {}\n']
    }

    def "a clean file passes the content rules"() {
        expect:
        Determinism.assertContent([new GeneratedFile('A.java', '@jakarta.annotation.Generated("x")\nclass A {}\n')])
    }
}
