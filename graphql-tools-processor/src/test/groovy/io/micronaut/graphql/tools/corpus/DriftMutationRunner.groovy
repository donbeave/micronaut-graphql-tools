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

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Runs the drift mutation tests (#89) over the binding corpus. The base of a mutant is a corpus case in Java or Groovy that binds
 * cleanly at startup and at compile time; the mutant changes its SDL ({@link SchemaMutator}) and nothing else. The sources of the
 * case are compiled <b>once</b> (the fixture), and every mutant restarts the application on those classes with the mutated schema, and
 * runs the checked compilation (the compile-time check, which reads the schema directory, so it compiles again). A mutant is judged by:
 *
 * <ul>
 *   <li><b>breaking</b>: the startup must fail, and a finding must name the mutated element. A mutant that binds is a bug, reported as
 *   such. The compile-time verdict is judged by {@link Parity} against the startup: it reports no error that the startup does not, and
 *   reports every finding of a per-class code (a whole-program finding, such as a field that no method binds, is the startup's to
 *   report);</li>
 *   <li><b>equivalent</b>: the startup must bind, to the same binding manifest and the same introspection as the unchanged case, and
 *   the compile-time check must still pass.</li>
 * </ul>
 *
 * <p>A mutant whose schema is not a valid schema (the startup fails outside the bindings) is not a drift mutant and is counted as
 * invalid. {@code seed} orders the choice of the mutants and seeds the shuffles; {@code limit} (0 for all) is how many mutants a sample runs, in whole cases.</p>
 */
@CompileStatic
final class DriftMutationRunner {

    final Corpus corpus
    final long seed
    final int limit
    final Binder runtimeBinder = new RuntimeBinder(true)
    final CompileTimeBinder compileTimeBinder = new VisitorCompileTimeBinder()
    /** one line per mutant: the outcome and the time */
    final Map<String, String> outcomes = new ConcurrentHashMap<>()
    private final Map<Language, CaseCompiler> compilers

    DriftMutationRunner(Corpus corpus, List<CaseCompiler> compilers, long seed, int limit) {
        this.corpus = corpus
        this.seed = seed
        this.limit = limit
        this.compilers = compilers.collectEntries { [(it.language): it] } as Map<Language, CaseCompiler>
    }

    /** A case in one language and the mutants chosen for it. */
    static final class Unit {

        final CorpusCase corpusCase
        final Language language
        final List<Mutant> mutants

        Unit(CorpusCase corpusCase, Language language, List<Mutant> mutants) {
            this.corpusCase = corpusCase
            this.language = language
            this.mutants = mutants
        }

        @Override
        String toString() {
            return "${corpusCase.name} [${language.dir}] (${mutants.size()} mutants)"
        }

    }

    /** @return whether a case in a language is a base of mutants: it binds cleanly, with every check at its default, on a compiler that has a checked mode */
    boolean eligible(CorpusCase c, Language language) {
        CaseCompiler compiler = compilers.get(language)
        if (!(compiler instanceof CheckedCaseCompiler) || c.processorAbsent() || c.checksOff() || !c.jarSchemaFiles().empty ||
                c.schemaFiles().empty || !(c.compile.getOrDefault('schema-dirs', 'case') == 'case') ||
                c.config.keySet().any { it.contains('.strict') || it.contains('.checks.') }) {
            return false
        }
        try {
            Verdict runtime = c.expectedVerdict(language, 'runtime.txt')
            Verdict compile = c.expectedVerdict(language, compiler.compileVerdictFile(c))
            return runtime != null && compile != null && runtime.findings.empty && compile.findings.empty
        } catch (IllegalArgumentException ignored) {
            return false
        }
    }

    /**
     * @return the units to run: every base case with all its mutants, or, with a limit, the cases that the seed puts first until their
     * mutants reach the limit (a case is compiled once, so a sample takes whole cases rather than single mutants)
     */
    List<Unit> units() {
        String only = CorpusFixtures.only()
        List<Unit> all = []
        corpus.units().findAll { compilers.containsKey((Language) it[1]) && (only == null || ((CorpusCase) it[0]).name ==~ only) }.each { List<Object> unit ->
            CorpusCase c = (CorpusCase) unit[0]
            Language language = (Language) unit[1]
            if (eligible(c, language)) {
                all.add(new Unit(c, language, SchemaMutator.mutants(schemaFiles(c), seed)))
            }
        }
        if (limit <= 0) {
            return all
        }
        List<Unit> shuffled = new ArrayList<>(all)
        Collections.shuffle(shuffled, new Random(seed))
        List<Unit> chosen = []
        int count = 0
        for (Unit unit : shuffled) {
            if (count >= limit) {
                break
            }
            chosen.add(unit)
            count += unit.mutants.size()
        }
        return chosen.sort { it.corpusCase.name + ' ' + it.language.dir }
    }

    static Map<String, String> schemaFiles(CorpusCase c) {
        Map<String, String> files = new LinkedHashMap<>()
        c.schemaFiles().each { Path file -> files.put(file.fileName.toString(), Files.readString(file)) }
        return files
    }

    /**
     * Runs the mutants of one case in one language.
     *
     * @throws AssertionError listing every mutant that is judged wrong
     */
    void run(Unit unit) {
        CorpusCase base = unit.corpusCase
        CaseCompiler compiler = compilers.get(unit.language)
        Path work = Files.createTempDirectory('mutation-' + base.name.replace('/', '-') + '-' + unit.language.dir + '-')
        try {
            CompiledCase compiled = compiler.compile(base, work.resolve('unchecked'))
            if (!compiled.success) {
                throw new AssertionError("${unit}: the sources do not compile with the checks off:\n  ${compiled.errors.join('\n  ')}".toString())
            }
            Verdict baseline = runtimeBinder.bind(new BindRequest(base, unit.language, compiler, compiled, work.resolve('baseline')))
            if (!baseline.bound || baseline.manifest == null || baseline.responses == null) {
                throw new AssertionError("${unit}: the unchanged case does not bind with a manifest: ${baseline}".toString())
            }
            List<String> failures = []
            unit.mutants.eachWithIndex { Mutant mutant, int i ->
                long start = System.nanoTime()
                String problem = null
                String outcome = 'ok'
                if (mutant.breaking && !bound(baseline.manifest, mutant.element)) {
                    outcomes.put("${base.name} [${unit.language.dir}] ${mutant.id}".toString(), 'skipped')
                    return
                }
                try {
                    CorpusCase mutated = mutate(base, mutant, work.resolve('mutant-' + i))
                    BindRequest request = new BindRequest(mutated, unit.language, compiler, compiled, work.resolve('mutant-' + i + '-work'))
                    Verdict runtime = runtimeBinder.bind(request)
                    Verdict compile = compileTimeBinder.bind(request)
                    problem = judge(mutated, unit.language, mutant, baseline, runtime, compile)
                } catch (AssertionError e) {
                    if (mutant.breaking && e.message.contains('outside the bindings')) {
                        outcome = 'invalid'
                    } else {
                        problem = e.message
                    }
                }
                if (problem != null) {
                    outcome = 'FAIL'
                    failures.add("${mutant.id}: ${problem}".toString())
                }
                outcomes.put("${base.name} [${unit.language.dir}] ${mutant.id}".toString(),
                        String.format('%-7s %6d ms', outcome, (System.nanoTime() - start).intdiv(1_000_000)))
            }
            if (!failures.empty) {
                throw new AssertionError("${unit}:\n  ${failures.join('\n  ')}".toString())
            }
        } finally {
            work.toFile().deleteDir()
        }
    }

    /** @return what is wrong with the verdicts of a mutant, or {@code null}; a gap that the list of known gaps names is not wrong */
    String judge(CorpusCase mutated, Language language, Mutant mutant, Verdict baseline, Verdict runtime, Verdict compile) {
        List<String> problems = []
        if (mutant.breaking) {
            if (runtime.bound) {
                gap(problems, language, mutant, 'silent', 'BUG: the mutant binds silently, the startup reports nothing although the schema no longer matches the code')
            } else {
                if (!runtime.findings.any { mutant.names(it) }) {
                    gap(problems, language, mutant, 'unnamed', "the startup fails but no finding names the mutated element (${mutant.names}): ${runtime.lines()}".toString())
                }
                Set<String> runtimeKeys = runtime.keys()
                compile.errors.findAll { !runtimeKeys.contains(it.key()) && !mutant.names(it) }.each {
                    gap(problems, language, mutant, 'compile-extra', "the compile-time check reports '${it.key()}', which neither the startup reports nor names the mutated element".toString())
                }
                Parity.check(mutated.name, language, runtime, compile, corpus.divergences, [] as Set<String>, corpus.exceptions)
                        .findAll { it.startsWith('completeness') }.each { gap(problems, language, mutant, 'compile-missed', it) }
            }
        } else {
            if (!runtime.bound) {
                problems.add("the startup fails: ${runtime.lines()}".toString())
            } else {
                if (normalize(runtime.manifest) != normalize(baseline.manifest)) {
                    problems.add("the binding manifest changes: ${lineDiff(normalize(baseline.manifest), normalize(runtime.manifest))}".toString())
                }
                if (runtime.responses != baseline.responses) {
                    problems.add('the introspection of the schema changes')
                }
            }
            if (compile.available && !compile.findings.empty) {
                problems.add("the compile-time check reports: ${compile.lines()}".toString())
            }
        }
        return problems.empty ? null : problems.join('; ')
    }

    private void gap(List<String> problems, Language language, Mutant mutant, String category, String text) {
        if (!corpus.mutationGaps.covers(category, mutant.kind, language)) {
            problems.add(text)
        }
    }

    /** @return whether a line of the manifest is the entry of the element: {@code Type.field}, {@code Type.field(args)} or {@code Enum.VALUE} */
    static boolean bound(String manifest, String element) {
        return manifest.readLines().any { it.startsWith(element + ':') || it.startsWith(element + '(') || it.startsWith(element + ' ->') }
    }

    /** The manifest with the arguments of every field in alphabetical order: it prints the field as the schema writes it, and the arguments bind by name. */
    static String normalize(String manifest) {
        return manifest.readLines().collect { String line ->
            java.util.regex.Matcher m = (line =~ /^([^\s(]+)\((.*?)\)(: .*)$/)
            m.matches() ? m.group(1) + '(' + m.group(2).split(', ').toList().sort().join(', ') + ')' + m.group(3) : line
        }.join('\n')
    }

    private static String lineDiff(String before, String after) {
        List<String> a = before.readLines()
        List<String> b = after.readLines()
        return (a.findAll { !b.contains(it) }.collect { '-' + it } + b.findAll { !a.contains(it) }.collect { '+' + it }).join(' | ')
    }

    /** Copies the case to a corpus root of its own with the schema of the mutant in place of its schema files. */
    private static CorpusCase mutate(CorpusCase base, Mutant mutant, Path root) {
        Path dir = root.resolve(base.name)
        Files.createDirectories(dir)
        Path source = base.dir
        try (Stream<Path> paths = Files.walk(source)) {
            ((List<Path>) paths.collect(Collectors.toList())).each { Path path ->
                Path target = dir.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else if (!path.fileName.toString().endsWith('.graphqls') || path.parent != source) {
                    Files.copy(path, target)
                }
            }
        }
        mutant.files.each { String name, String text -> Files.writeString(dir.resolve(name), text) }
        return CorpusCase.load(root, dir)
    }

    /** @return the lines of the list of known gaps that no mutant fell under: meaningful after a run of every mutant */
    List<String> staleGaps() {
        return corpus.mutationGaps.unused()
    }

    /** @return one line per mutant, by case, with the outcome and the time */
    String report() {
        return outcomes.entrySet().sort { it.key }.collect { "${it.value}  ${it.key}".toString() }.join('\n') + '\n'
    }

}
