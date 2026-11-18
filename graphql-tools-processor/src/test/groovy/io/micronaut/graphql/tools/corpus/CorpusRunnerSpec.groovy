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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The tests of the corpus runner itself (#142): the layout rules, the five assertions, the divergence ceiling and the
 * golden update, on small corpora written to a temporary directory and judged by stub binders, so that no compiler and
 * no application context is involved.
 */
class CorpusRunnerSpec extends Specification {

    static final Finding UNBOUND = new Finding('unbound-resolver-method', 'example.A#stray()')
    static final Finding MISSING = new Finding('field-not-bound', 'Query.missing', 'schema.graphqls:3:3')

    @TempDir
    Path dir

    // ------------------------------------------------------------------------------------------------ fixtures

    /** One case `a` in Java, with the given expected files. */
    private Path corpus(Map<String, String> expected = [:], String divergences = 'max-entries: 0\n', Map<String, String> more = [:],
                        String exceptions = 'max-entries: 0\n') {
        Map<String, String> files = [
                'a/case.properties'          : 'description=a\ntags=area:test\n',
                'a/schema.graphqls'          : 'type Query { a: String }\n',
                'a/java/example/A.java'      : 'package example; class A { }\n',
                'corpus-divergences.txt'     : divergences,
                'corpus-soundness-exceptions.txt': exceptions,
        ]
        expected.each { name, text -> files.put('a/expected/' + name, text) }
        files.putAll(more)
        files.each { name, text ->
            Path file = dir.resolve(name)
            Files.createDirectories(file.parent)
            Files.writeString(file, text)
        }
        return dir
    }

    private CorpusRunner runner(Path root, Verdict runtime, Verdict compile = Verdict.unavailable(), boolean update = false) {
        return new CorpusRunner(new Corpus(root), [new StubCompiler()], new StubBinder('runtime', runtime),
                new StubCompileTimeBinder(compile), update)
    }

    static class StubCompiler implements CaseCompiler {
        Language getLanguage() { Language.JAVA }

        CompiledCase compile(CorpusCase corpusCase, Path work) {
            return new CompiledCase(true, Files.createDirectories(work), [])
        }
    }

    static class StubBinder implements Binder {
        final String name
        final Verdict verdict

        StubBinder(String name, Verdict verdict) {
            this.name = name
            this.verdict = verdict
        }

        Verdict bind(BindRequest request) { verdict }
    }

    static class StubCompileTimeBinder implements CompileTimeBinder {
        final Verdict verdict

        StubCompileTimeBinder(Verdict verdict) {
            this.verdict = verdict
        }

        String getName() { 'compile-time (stub)' }

        Verdict bind(BindRequest request) { verdict }
    }

    /** no fragment: the run must pass; else it must fail with a message that contains it */
    private static boolean matches(String message, String fragment) {
        return fragment == null ? message == null : message?.contains(fragment)
    }

    private static String message(Closure<?> action) {
        try {
            action.call()
        } catch (AssertionError e) {
            return e.message
        }
        return null
    }

    // ------------------------------------------------------------------------------------------------ cases are data

    void "a case directory is discovered without any change of the harness, in each language it has sources for"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n'])
        Files.createDirectories(dir.resolve('b/c/groovy/example'))
        Files.writeString(dir.resolve('b/c/case.properties'), 'description=nested\n')
        Files.writeString(dir.resolve('b/c/schema.graphqls'), 'type Query { a: String }\n')
        Files.writeString(dir.resolve('b/c/groovy/example/C.groovy'), 'class C { }\n')
        Files.createDirectories(dir.resolve('b/c/expected'))
        Files.writeString(dir.resolve('b/c/expected/runtime.txt'), 'ok\n')

        when:
        Corpus corpus = new Corpus(root)

        then:
        corpus.problems == []
        corpus.cases*.name == ['a', 'b/c']
        corpus.units().collect { [it[0].toString(), ((Language) it[1]).dir] } == [['a', 'java'], ['b/c', 'groovy']]
    }

    void "the properties of a case: tags, environments and the runtime configuration"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n'])
        Files.writeString(dir.resolve('a/case.properties'),
                'description=a\ntags=lang:all, kotlin:pr\nenvironments=test, other\nconfig.micronaut.graphql.tools.strict=false\n')

        when:
        CorpusCase c = new Corpus(root).byName('a')

        then:
        c.tags == ['lang:all', 'kotlin:pr']
        c.environments == ['test', 'other']
        c.config == ['micronaut.graphql.tools.strict': 'false']
    }

    // ------------------------------------------------------------------------------------------------ the verdict assertions

    void "a bound case passes and a failing case reports the missing and the unexpected findings"() {
        given:
        Path root = corpus(['runtime.txt': expected])
        CorpusCase c = new Corpus(root).byName('a')

        expect:
        matches(message { runner(root, actual).run(c, Language.JAVA) }, fragment)

        where:
        expected                                                         | actual                        | fragment
        'ok\n'                                                           | Verdict.bound()               | null
        'field-not-bound Query.missing schema.graphqls:3:3\n'    | Verdict.failed([MISSING])     | null
        'ok\n'                                                           | Verdict.failed([MISSING])     | '+ actual:   field-not-bound Query.missing schema.graphqls:3:3'
        'field-not-bound Query.missing schema.graphqls:3:3\n'    | Verdict.bound()               | '- expected: field-not-bound Query.missing schema.graphqls:3:3'
        'field-not-bound Query.missing schema.graphqls:4:3\n'    | Verdict.failed([MISSING])     | '- expected: field-not-bound Query.missing schema.graphqls:4:3'
    }

    void "the binding manifest of a bound startup is compared where one is recorded"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n', 'manifest.txt': 'Query.a: String -> root\n'])
        CorpusCase c = new Corpus(root).byName('a')

        expect:
        message { runner(root, Verdict.bound('Query.a: String -> root\n')).run(c, Language.JAVA) } == null
        message { runner(root, Verdict.bound('Query.a: String -> property\n')).run(c, Language.JAVA) }
                .contains('the binding manifest differs from expected/manifest.txt')
        message { runner(root, Verdict.bound()).run(c, Language.JAVA) }.contains('the case does not record a manifest')
    }

    void "the time of each case and language is recorded"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n'])
        CorpusRunner runner = runner(root, Verdict.bound())

        when:
        runner.run(runner.corpus.byName('a'), Language.JAVA)

        then:
        runner.timings.keySet() == ['a [java]'] as Set
        runner.report() ==~ /\s*\d+ ms  a \[java\]\n/
    }

    // ------------------------------------------------------------------------------------------------ the layout rules

    void "orphan and stale files fail: #name"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n'])
        mutate.call(dir)

        expect:
        new Corpus(root).problems.any { it.contains(fragment) }

        where:
        [name, mutate, fragment] << orphanCases()
    }

    static List<List<Object>> orphanCases() {
        Closure<?> write = { String file, String text -> { Path d -> Files.createDirectories(d.resolve(file).parent); Files.writeString(d.resolve(file), text) } }
        return [
                ['an unknown file in the case', write('a/notes.txt', 'x'), "orphan entry 'notes.txt'"],
                ['an unknown expected file', write('a/expected/verdict.txt', 'x'), 'orphan expected file expected/verdict.txt'],
                ['a file that is not a source', write('a/java/example/a.txt', 'x'), 'orphan file java/example/a.txt'],
                ['an expected directory of no language', { Path d -> Files.createDirectories(d.resolve('a/expected/kotlin')) }, 'orphan expected directory expected/kotlin/'],
                ['a file at the corpus root', write('stray.txt', 'x'), 'orphan file stray.txt'],
                ['a directory without a case', { Path d -> Files.createDirectories(d.resolve('empty/dir')) }, 'orphan directory empty'],
                ['an override equal to the base', write('a/expected/java/runtime.txt', 'ok\n'), 'stale override expected/java/runtime.txt'],
                ['an unsorted verdict', write('a/expected/runtime.txt', 'B x -\nA x -\n'), 'runtime.txt (java) is not sorted'],
                ['a malformed verdict', write('a/expected/runtime.txt', 'broken\n'), 'Not a finding line'],
                ['no expected runtime verdict', { Path d -> Files.delete(d.resolve('a/expected/runtime.txt')) }, 'no expected runtime verdict for java'],
                ['no schema', { Path d -> Files.delete(d.resolve('a/schema.graphqls')) }, 'no *.graphqls schema file'],
                ['an unknown property', write('a/case.properties', 'verdict=ok\n'), "unknown key 'verdict'"],
                ['lang:all without three languages', write('a/case.properties', 'tags=lang:all\n'), 'tag lang:all needs'],
        ] as List<List<Object>>
    }

    // ------------------------------------------------------------------------------------------------ parity

    void "soundness holds on the recorded expectations: a compile finding the startup does not report is a problem"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n', 'compile.txt': 'unbound-resolver-method example.A#stray() -\n'])

        expect:
        new Corpus(root).problems.any { it.contains("soundness: the compile side reports 'unbound-resolver-method example.A#stray()' but the startup does not") }
    }

    void "a whole-program fixture passes the compile and fails at startup, and a per-class one fails both sides with the same code and coordinate"() {
        given:
        Path whole = corpus(['runtime.txt': MISSING.line() + '\n', 'compile.txt': 'ok\n'])
        Path perClass = Files.createDirectories(dir.resolve('b'))
        Files.createDirectories(perClass.resolve('java/example'))
        Files.createDirectories(perClass.resolve('expected'))
        Files.writeString(perClass.resolve('case.properties'), 'description=b\n')
        Files.writeString(perClass.resolve('schema.graphqls'), 'type Query { a: String }\n')
        Files.writeString(perClass.resolve('java/example/A.java'), 'package example; class A { }\n')
        Files.writeString(perClass.resolve('expected/runtime.txt'), UNBOUND.line() + '\n')
        Files.writeString(perClass.resolve('expected/compile.txt'), UNBOUND.line() + '\n')

        expect:
        new Corpus(whole).problems == []
    }

    void "completeness: a per-class startup finding that the compile side does not report needs an entry in the divergence list"() {
        given:
        Path root = corpus(['runtime.txt': UNBOUND.line() + '\n', 'compile.txt': compile] as Map, divergences)

        expect:
        new Corpus(root).problems.any { it.contains('completeness') } == incomplete

        where:
        compile | divergences                                                                  | incomplete
        'ok\n'  | 'max-entries: 0\n'                                                           | true
        'ok\n'  | 'max-entries: 1\na java unbound-resolver-method example.A#stray()\n'        | false
        'ok\n'  | 'max-entries: 1\na all unbound-resolver-method example.A#stray()\n'         | false
        'ok\n'  | 'max-entries: 1\nb java unbound-resolver-method example.A#stray()\n'        | true
    }

    // ------------------------------------------------------------------------------------------------ the soundness exceptions

    void "a documented exception excuses one compile error of one case and language, and nothing else"() {
        given:
        Path root = corpus(['runtime.txt': 'field-not-bound Query.missing schema.graphqls:3:3\n', 'compile.txt': 'argument-mismatch Query.a -\nunbound-resolver-method example.A#stray() -\n'],
                'max-entries: 0\n', [:], exceptions)

        expect:
        new Corpus(root).problems.any { it.contains("soundness: the compile side reports 'unbound-resolver-method example.A#stray()'") } == other
        new Corpus(root).problems.any { it.contains("soundness: the compile side reports 'argument-mismatch Query.a'") } == excused

        where:
        exceptions                                                                                       | excused | other
        'max-entries: 0\n'                                                                               | true    | true
        'max-entries: 1\na java argument-mismatch Query.a customizer-claimed-field\n'                    | false   | true
        'max-entries: 1\na all argument-mismatch Query.a customizer-claimed-field\n'                     | false   | true
        'max-entries: 1\nb java argument-mismatch Query.a customizer-claimed-field\n'                    | true    | true
        'max-entries: 1\na groovy argument-mismatch Query.a customizer-claimed-field\n'                  | true    | true
    }

    void "an exception is stale when the compile side does not report the error or the startup does, and it names a known reason"() {
        given:
        Path root = corpus(['runtime.txt': runtime, 'compile.txt': compile], 'max-entries: 0\n', [:], 'max-entries: 1\n' + entry + '\n')

        expect:
        new Corpus(root).problems.any { it.contains(fragment) }

        where:
        runtime                    | compile                      | entry                                                          | fragment
        'ok\n'                     | 'ok\n'                       | 'a java argument-mismatch Query.a customizer-claimed-field'    | 'is stale: java/compile.txt has no such error'
        'argument-mismatch Query.a -\n' | 'argument-mismatch Query.a -\n' | 'a java argument-mismatch Query.a customizer-claimed-field' | 'is stale: java/runtime.txt reports it'
        'ok\n'                     | 'argument-mismatch Query.a -\n' | 'a java argument-mismatch Query.a because'                  | "unknown reason 'because'"
        'ok\n'                     | 'argument-mismatch Query.a -\n' | 'gone java argument-mismatch Query.a customizer-claimed-field' | 'names no case'
        'ok\n'                     | 'argument-mismatch Query.a -\n' | 'a java argument-mismatch Query.a'                             | 'expected'
    }

    void "the exception list may only shrink: the ceiling must equal the number of entries"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n', 'compile.txt': 'argument-mismatch Query.a -\n'], 'max-entries: 0\n', [:], exceptions)

        expect:
        new Corpus(root).problems.any { it.contains(fragment) } == (fragment != null)

        where:
        exceptions                                                                           | fragment
        'max-entries: 1\na java argument-mismatch Query.a customizer-claimed-field\n'        | null
        'max-entries: 0\na java argument-mismatch Query.a customizer-claimed-field\n'        | 'exceed the ceiling max-entries: 0'
        'max-entries: 2\na java argument-mismatch Query.a customizer-claimed-field\n'        | 'lower the ceiling to 1'
        'a java argument-mismatch Query.a customizer-claimed-field\n'                         | "the header 'max-entries: N' is missing"
    }

    void "a compile warning is outside the soundness rule and counts as the compile side seeing the finding"() {
        given:
        Path root = corpus(['runtime.txt': runtime, 'compile.txt': 'warning class-not-introspected Filter schema.graphqls:3:3\n'])

        expect:
        new Corpus(root).problems.any { it.contains('soundness') } == false
        new Corpus(root).problems.any { it.contains('completeness') } == false

        where:
        runtime << ['ok\n', 'class-not-introspected Filter schema.graphqls:3:3\n']
    }

    void "a whole-program code is not a completeness gap"() {
        given:
        Path root = corpus(['runtime.txt': MISSING.line() + '\n'])

        expect:
        new Corpus(root).problems == []
    }

    // ------------------------------------------------------------------------------------------------ the divergence list

    void "the divergence list may only shrink: the ceiling must equal the number of entries"() {
        given:
        Path root = corpus(['runtime.txt': UNBOUND.line() + '\n', 'compile.txt': 'ok\n'], divergences)

        expect:
        new Corpus(root).problems.any { it.contains(fragment) } == (fragment != null)

        where:
        divergences                                                                          | fragment
        'max-entries: 1\na java unbound-resolver-method example.A#stray()\n'                 | null
        'max-entries: 0\na java unbound-resolver-method example.A#stray()\n'                 | '1 entries exceed the ceiling max-entries: 0; the list may only shrink'
        'max-entries: 2\na java unbound-resolver-method example.A#stray()\n'                 | 'below the ceiling max-entries: 2; lower the ceiling to 1'
        'a java unbound-resolver-method example.A#stray()\n'                                 | "the header 'max-entries: N' is missing"
    }

    void "an entry that no longer diverges, or that names nothing, is stale"() {
        given:
        Path root = corpus(['runtime.txt': runtime, 'compile.txt': compile], 'max-entries: 1\n' + entry + '\n')

        expect:
        new Corpus(root).problems.any { it.contains(fragment) }

        where:
        runtime                    | compile                                   | entry                                                     | fragment
        'ok\n'                     | 'ok\n'                                    | 'a java unbound-resolver-method example.A#stray()'        | "is stale: java/runtime.txt has no such finding"
        UNBOUND.line() + '\n'      | UNBOUND.line() + '\n'                     | 'a java unbound-resolver-method example.A#stray()'        | "is stale: java/compile.txt reports it"
        UNBOUND.line() + '\n'      | 'ok\n'                                    | 'gone java unbound-resolver-method example.A#stray()'     | "names no case"
        UNBOUND.line() + '\n'      | 'ok\n'                                    | 'a kotlin unbound-resolver-method example.A#stray()'      | "the case has no such language"
    }

    // ------------------------------------------------------------------------------------------------ both binders

    void "where the compile-time backend exists, its verdict must equal compile.txt and runtime and compile must agree"() {
        given:
        Path root = corpus(['runtime.txt': runtimeText, 'compile.txt': compileText])
        CorpusCase c = new Corpus(root).byName('a')

        expect:
        matches(message { runner(root, runtime, compile).run(c, Language.JAVA) }, fragment)

        where:
        runtimeText           | compileText           | runtime                      | compile                       | fragment
        UNBOUND.line() + '\n' | UNBOUND.line() + '\n' | Verdict.failed([UNBOUND])    | Verdict.failed([UNBOUND])     | null
        MISSING.line() + '\n' | 'ok\n'                | Verdict.failed([MISSING])    | Verdict.bound()               | null
        MISSING.line() + '\n' | 'ok\n'                | Verdict.failed([MISSING])    | Verdict.failed([UNBOUND])     | 'compile-time (stub) verdict differs from expected/compile.txt'
        MISSING.line() + '\n' | UNBOUND.line() + '\n' | Verdict.failed([MISSING])    | Verdict.failed([UNBOUND])     | "soundness: the compile side reports 'unbound-resolver-method example.A#stray()' but the startup does not"
    }

    void "soundness is asserted on the actual verdicts, and no divergence entry excuses a false compile error"() {
        given:
        Path root = corpus(['runtime.txt': MISSING.line() + '\n', 'compile.txt': UNBOUND.line() + '\n'])
        CorpusCase c = new Corpus(root).byName('a')

        expect: 'the recorded expectation is itself unsound'
        new Corpus(root).problems.any { it.contains('soundness') }

        and: 'the run fails on the actual verdicts too'
        message { runner(root, Verdict.failed([MISSING]), Verdict.failed([UNBOUND])).run(c, Language.JAVA) }.contains('soundness')
    }

    void "the compile-time backend does not exist yet: the stub says so and the runner needs no compile.txt"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n'])

        expect:
        !NoCompileTimeBinder.INSTANCE.bind(null).available
        message { runner(root, Verdict.bound()).run(new Corpus(root).byName('a'), Language.JAVA) } == null
    }

    // ------------------------------------------------------------------------------------------------ the golden update

    void "the golden update rewrites the expected files from what the binders say, and the result passes"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n', 'manifest.txt': ''])
        Verdict bound = Verdict.bound('Query.a: String -> root\n')

        when:
        runner(root, Verdict.failed([MISSING, UNBOUND]), Verdict.unavailable(), true).run(new Corpus(root).byName('a'), Language.JAVA)

        then: 'a failing startup has no manifest, so the recorded manifest expectation is removed'
        !Files.exists(dir.resolve('a/expected/manifest.txt'))
        Files.readString(dir.resolve('a/expected/runtime.txt')) == "field-not-bound Query.missing schema.graphqls:3:3\nunbound-resolver-method example.A#stray() -\n"

        when: 'the expectation is recorded again (an empty manifest file marks the case)'
        Files.writeString(dir.resolve('a/expected/manifest.txt'), '')
        runner(root, bound, Verdict.unavailable(), true).run(new Corpus(root).byName('a'), Language.JAVA)

        then:
        Files.readString(dir.resolve('a/expected/runtime.txt')) == 'ok\n'
        Files.readString(dir.resolve('a/expected/manifest.txt')) == 'Query.a: String -> root\n'
        message { runner(root, bound).run(new Corpus(root).byName('a'), Language.JAVA) } == null
    }

    void "the golden update keeps the verdict of a language it does not compile (Kotlin) as an override"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n'], 'max-entries: 0\n', ['a/kotlin/example/A.kt': 'package example\n'])

        when:
        runner(root, Verdict.failed([MISSING]), Verdict.unavailable(), true).run(new Corpus(root).byName('a'), Language.JAVA)

        then:
        Files.readString(dir.resolve('a/expected/runtime.txt')) == "field-not-bound Query.missing schema.graphqls:3:3\n"
        Files.readString(dir.resolve('a/expected/kotlin/runtime.txt')) == 'ok\n'
        new Corpus(root).problems == []
    }

    void "a duplicated divergence entry is a problem"() {
        given:
        Path root = corpus(['runtime.txt': 'ok\n'], 'max-entries: 2\na all field-not-bound Query.x\na all field-not-bound Query.x\n')

        expect:
        new Corpus(root).problems.any { it.contains('is listed twice') }
    }

}
