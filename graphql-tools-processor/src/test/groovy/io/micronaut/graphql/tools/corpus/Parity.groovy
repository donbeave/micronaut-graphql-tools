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
import io.micronaut.graphql.tools.binding.BindingCode

/**
 * The parity rules between the compile-time and the startup verdict of one case, at the default severities.
 *
 * <ul>
 *   <li><b>Soundness.</b> Every compile error is also a startup finding with the same code and coordinate. It fails outright:
 *   a false compile error is a bug. Only the documented soundness exceptions excuse an error ({@link SoundnessExceptions}: one line
 *   per case and finding, with one of five reasons, the startup failing or binding differently on purpose). A compile-time
 *   warning is not an error and is outside the rule.</li>
 *   <li><b>Completeness.</b> A startup finding of a per-class code ({@link #PER_CLASS_CODES}) that the compile side
 *   does not report, as an error or as a warning, is a gap, and fails unless {@code corpus-divergences.txt} lists it. A startup finding of any other
 *   code (whole-program: field not bound, reachability, ambiguity, the dictionary) is not a gap: the compile side
 *   cannot see it.</li>
 * </ul>
 *
 * <p>The set of per-class codes is the design of the compile-time checks: the five check codes of the parity set and the
 * per-class failures. It is read from the code registry (#237): the codes that run at compile time, without the codes that exist
 * only there.</p>
 */
@CompileStatic
final class Parity {

    static final Set<String> PER_CLASS_CODES = BindingCode.values()
            .findAll { it.compileTime() && !it.code().startsWith('processor-') && it != BindingCode.SCHEMA_INVALID }
            *.code() as Set<String>

    private Parity() {
    }

    /**
     * @param name       the case, for the messages
     * @param language   the language
     * @param runtime    the startup verdict
     * @param compile    the compile-time verdict; {@code null} if none is recorded (read as: reports nothing)
     * @param divergences the known gaps
     * @param relaxed    the codes that the case relaxes at startup, which the compile-time check cannot (see {@link #relaxedCodes})
     * @param exceptions the documented exceptions to soundness
     * @return the violations, empty if the two verdicts are in parity
     */
    static List<String> check(String name, Language language, Verdict runtime, Verdict compile, Divergences divergences,
                              Set<String> relaxed = [] as Set<String>, SoundnessExceptions exceptions = SoundnessExceptions.NONE) {
        List<String> violations = []
        Set<String> runtimeKeys = runtime.keys()
        Set<String> compileKeys = compile == null ? [] as Set<String> : compile.keys()
        // a code that the case relaxes at startup is outside the rule: the compile error cannot be relaxed
        (compile == null ? [] : compile.errors).findAll {
            !(it.code in relaxed) && !runtimeKeys.contains(it.key()) && !exceptions.covers(name, language, it)
        }.collect { it.key() }.sort().each {
            violations.add("soundness: the compile side reports '${it}' but the startup does not (a false compile error)".toString())
        }
        runtime.findings.findAll { it.code in PER_CLASS_CODES && !compileKeys.contains(it.key()) }.each { Finding f ->
            if (!divergences.covers(name, language, f)) {
                violations.add("completeness: the startup reports '${f.key()}' but the compile side does not, and it is not in ${Corpus.DIVERGENCES}".toString())
            }
        }
        return violations
    }

    /**
     * @return the codes that the case relaxes at startup: the strict checks for {@code strict=false}, and the code of each
     * {@code checks.<code>} that is {@code warn} or {@code ignore}
     */
    static Set<String> relaxedCodes(Map<String, String> config) {
        String prefix = 'micronaut.graphql.tools.checks.'
        Set<String> codes = [] as Set<String>
        config.each { String key, String value ->
            if (key == 'micronaut.graphql.tools.strict' && value.trim().equalsIgnoreCase('false')) {
                codes.addAll(BindingCode.values().findAll { it.check() }*.code())
            } else if (key.startsWith(prefix) && value.trim().toLowerCase() in ['warn', 'ignore']) {
                codes.add(key.substring(prefix.length()))
            }
        }
        return codes
    }

    /** @return the violations of the recorded expectations of every case and language */
    static List<String> checkExpected(Corpus corpus) {
        List<String> result = []
        corpus.cases.each { CorpusCase c ->
            c.languages().each { Language language ->
                Verdict runtime
                Verdict compile
                try {
                    runtime = c.expectedVerdict(language, 'runtime.txt')
                    compile = c.expectedVerdict(language, 'compile.txt')
                } catch (IllegalArgumentException ignored) {
                    return // malformed: reported with the problems of the case
                }
                if (runtime == null) {
                    return
                }
                check(c.name, language, runtime, compile, corpus.divergences, relaxedCodes(c.config), corpus.exceptions)
                        .each { result.add("${c.name} [${language.dir}]: ${it}".toString()) }
            }
        }
        return result
    }

}
