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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the cases of a {@link Corpus}: compiles one language of a case in process, twice (the checks off, which is what is
 * started, and the checks on, which is what a build does), starts the first through the {@link RuntimeBinder}, and asserts, per
 * case and language:
 *
 * <ol>
 *   <li>the compilation with {@code checks=false} succeeds, whatever the case: the escape hatch compiles a failing case clean;</li>
 *   <li>the startup verdict equals {@code expected/runtime.txt} (code, coordinate and schema location);</li>
 *   <li>the binding manifest of a bound startup equals {@code expected/manifest.txt}, where there is one;</li>
 *   <li>where the {@link CompileTimeBinder} is available (Java and Groovy): the compilation with the checks on reports exactly the
 *   findings of {@code expected/compile.txt}, as errors that fail it and warnings that do not;</li>
 *   <li>soundness: every compile error is a startup finding with the same code and coordinate; only the documented exceptions to
 *   the soundness rule ({@code corpus-soundness-exceptions.txt}) excuse one;</li>
 *   <li>completeness: a startup finding of a per-class code that the compile side misses is listed in
 *   {@code corpus-divergences.txt}. Both rules are {@link Parity}; they are also asserted on the recorded expectations,
 *   so they hold for a language that has no compile-time backend yet (Kotlin).</li>
 * </ol>
 *
 * <p>Orphan and stale files, an unsorted or malformed expectation and the divergence ceiling are reported by
 * {@link Corpus#getProblems()}. With {@code update} the runner rewrites the expected files instead of comparing, and the
 * diff of the pull request is the review ({@code ./gradlew :micronaut-graphql-tools-processor:corpusUpdate}).</p>
 */
@CompileStatic
final class CorpusRunner {

    final Corpus corpus
    final Binder runtimeBinder
    final CompileTimeBinder compileTimeBinder
    final boolean update
    /** wall time per case and language, in milliseconds */
    final Map<String, Long> timings = new ConcurrentHashMap<>()
    private final Map<Language, CaseCompiler> compilers

    CorpusRunner(Corpus corpus, List<CaseCompiler> compilers, Binder runtimeBinder = new RuntimeBinder(),
                 CompileTimeBinder compileTimeBinder = NoCompileTimeBinder.INSTANCE, boolean update = false) {
        this.corpus = corpus
        this.runtimeBinder = runtimeBinder
        this.compileTimeBinder = compileTimeBinder
        this.update = update
        this.compilers = compilers.collectEntries { [(it.language): it] } as Map<Language, CaseCompiler>
    }

    /** @return the languages this runner compiles in process */
    Set<Language> languages() {
        return compilers.keySet()
    }

    /** @return each case with each language this runner compiles: the units to run */
    List<List<Object>> units() {
        String only = CorpusFixtures.only()
        return corpus.units().findAll {
            compilers.containsKey((Language) it[1]) && (only == null || ((CorpusCase) it[0]).name ==~ only)
        }
    }

    /**
     * Runs one case in one language.
     *
     * @throws AssertionError listing every violated assertion
     */
    void run(CorpusCase corpusCase, Language language) {
        long start = System.nanoTime()
        Path work = Files.createTempDirectory('corpus-' + corpusCase.name.replace('/', '-') + '-' + language.dir + '-')
        try {
            CaseCompiler compiler = compilers.get(language)
            CompiledCase compiled = compiler.compile(corpusCase, work.resolve('unchecked'))
            if (!compiled.success) {
                // the first of the two compilations: the escape hatch (checks=false) must compile every case, failing or not
                throw new AssertionError("${label(corpusCase, language)}: the sources do not compile with the checks off (checks=false):\n  ${compiled.errors.join('\n  ')}".toString())
            }
            BindRequest request = new BindRequest(corpusCase, language, compiler, compiled, work)
            Verdict runtime = runtimeBinder.bind(request)
            Verdict compile = compileTimeBinder.bind(request)
            if (update) {
                CorpusCase fresh = CorpusCase.load(corpus.root, corpusCase.dir)
                write(fresh, language, 'runtime.txt', runtime.lines().join('\n') + '\n')
                if (recorded(fresh, language, 'manifest.txt')) {
                    if (runtime.bound && runtime.manifest != null) {
                        write(fresh, language, 'manifest.txt', runtime.manifest)
                    } else if (!runtime.bound) {
                        // a startup that fails has no manifest: drop the expectation (its removal shows in the diff), the
                        // shared base file only when this language is the one that writes it
                        Files.deleteIfExists(fresh.expectedPath(language, 'manifest.txt'))
                        if (language == fresh.languages().first()) {
                            Files.deleteIfExists(fresh.expectedPath(null, 'manifest.txt'))
                        } else if (fresh.hasBaseExpected('manifest.txt')) {
                            // the shared manifest belongs to the languages that bind: the outcomes differ, so the case needs a split
                            throw new AssertionError("${label(corpusCase, language)}: the startup fails here but binds in another language of the case, which shares expected/manifest.txt: give this language its own case or record manifest.txt per language".toString())
                        }
                    }
                }
                if (compile.available && compilers.get(language).compileVerdictFile(corpusCase) == 'compile.txt') {
                    write(fresh, language, 'compile.txt', compile.lines().join('\n') + '\n')
                }
                return
            }
            List<String> failures = compare(corpusCase, language, runtime, compile)
            if (!failures.empty) {
                throw new AssertionError("${label(corpusCase, language)}:\n  ${failures.join('\n  ')}".toString())
            }
        } finally {
            timings.put(label(corpusCase, language), (System.nanoTime() - start).intdiv(1_000_000))
            work.toFile().deleteDir()
        }
    }

    List<String> compare(CorpusCase corpusCase, Language language, Verdict runtime, Verdict compile) {
        List<String> failures = []
        Verdict expectedRuntime = corpusCase.expectedVerdict(language, 'runtime.txt')
        if (expectedRuntime.lines() != runtime.lines()) {
            failures.add("${runtimeBinder.name} verdict differs from expected/runtime.txt:\n${diff(expectedRuntime.lines(), runtime.lines())}".toString())
        }
        String expectedManifest = corpusCase.expected(language, 'manifest.txt')
        if (expectedManifest != null) {
            if (!runtime.bound) {
                failures.add('expected/manifest.txt is recorded but the startup does not bind')
            } else if (runtime.manifest == null) {
                failures.add('expected/manifest.txt is recorded but the case does not record a manifest (manifest.enabled is off)')
            } else if (expectedManifest != runtime.manifest) {
                failures.add("the binding manifest differs from expected/manifest.txt:\n${diff(expectedManifest.readLines(), runtime.manifest.readLines())}".toString())
            }
        }
        String compileFile = compilers.get(language).compileVerdictFile(corpusCase)
        Verdict expectedCompile = corpusCase.expectedVerdict(language, compileFile)
        if (compile.available) {
            if (expectedCompile == null) {
                failures.add("the compile-time backend is available but expected/${compileFile} is not recorded".toString())
            } else if (expectedCompile.lines() != compile.lines()) {
                failures.add("${compileTimeBinder.name} verdict differs from expected/${compileFile}:\n${diff(expectedCompile.lines(), compile.lines())}".toString())
            }
            failures.addAll(Parity.check(corpusCase.name, language, runtime, compile, corpus.divergences, Parity.relaxedCodes(corpusCase.config), corpus.exceptions, corpusCase.checksOff()))
        }
        return failures
    }

    /** The timings, one line per case and language, slowest first. */
    String report() {
        return timings.entrySet().sort { -it.value }.collect { String.format('%6d ms  %s', it.value, it.key) }.join('\n') + '\n'
    }

    private static boolean recorded(CorpusCase c, Language language, String file) {
        return c.hasOwnExpected(language, file) || c.hasBaseExpected(file)
    }

    /**
     * Writes an expected file. A single-language case, or the first language of a case, writes the base file; another
     * language writes an override only where it differs from the base, and removes a stale one.
     */
    private void write(CorpusCase c, Language language, String file, String text) {
        Path base = c.expectedPath(null, file)
        if (language == c.languages().first() || !Files.exists(base)) {
            // a language this runner does not compile (Kotlin) reads the base file: keep what it reads today as its own
            // override, so that this update never changes a verdict that was not recomputed (corpusKotlin judges it)
            if (Files.exists(base)) {
                String old = Files.readString(base)
                c.languages().findAll { it != language && !compilers.containsKey(it) && !c.hasOwnExpected(it, file) }.each { Language skipped ->
                    if (old != text) {
                        Path own = c.expectedPath(skipped, file)
                        Files.createDirectories(own.parent)
                        Files.writeString(own, old, StandardCharsets.UTF_8)
                    }
                }
            }
            Files.createDirectories(base.parent)
            Files.writeString(base, text, StandardCharsets.UTF_8)
            return
        }
        Path own = c.expectedPath(language, file)
        if (Files.readString(base) == text) {
            Files.deleteIfExists(own)
            if (Files.isDirectory(own.parent) && Files.list(own.parent).count() == 0) {
                Files.delete(own.parent)
            }
        } else {
            Files.createDirectories(own.parent)
            Files.writeString(own, text, StandardCharsets.UTF_8)
        }
    }

    private static String label(CorpusCase c, Language language) {
        return "${c.name} [${language.dir}]".toString()
    }

    private static String diff(List<String> expected, List<String> actual) {
        List<String> lines = []
        expected.findAll { !actual.contains(it) }.each { lines.add("    - expected: ${it}".toString()) }
        actual.findAll { !expected.contains(it) }.each { lines.add("    + actual:   ${it}".toString()) }
        return lines.empty ? '    (same lines, different order)' : lines.join('\n')
    }

}
