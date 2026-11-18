package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.binding.BindingCode

/**
 * The parity rules between the compile-time and the startup verdict of one case (DD-4), at the default severities (D-30).
 *
 * <ul>
 *   <li><b>Soundness.</b> Every compile finding is also a startup finding with the same code and coordinate. It
 *   fails outright; no list excuses it (a false compile error is a bug).</li>
 *   <li><b>Completeness.</b> A startup finding of a per-class code ({@link #PER_CLASS_CODES}) that the compile side
 *   does not report is a gap, and fails unless {@code corpus-divergences.txt} lists it. A startup finding of any other
 *   code (whole-program: field not bound, reachability, ambiguity, the dictionary) is not a gap: the compile side
 *   cannot see it.</li>
 * </ul>
 *
 * <p>The set of per-class codes is the design of the compile-time checks: the five check codes of the parity set (DD-3) and the
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
     * @return the violations, empty if the two verdicts are in parity
     */
    static List<String> check(String name, Language language, Verdict runtime, Verdict compile, Divergences divergences,
                              Set<String> relaxed = [] as Set<String>) {
        List<String> violations = []
        Set<String> runtimeKeys = runtime.keys()
        Set<String> compileKeys = compile == null ? [] as Set<String> : compile.keys()
        // a code that the case relaxes at startup is outside the rule: the compile error cannot be relaxed (D-30, point 6)
        (compile == null ? [] : compile.findings).findAll { !(it.code in relaxed) && !runtimeKeys.contains(it.key()) }
                .collect { it.key() }.sort().each {
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
                check(c.name, language, runtime, compile, corpus.divergences, relaxedCodes(c.config))
                        .each { result.add("${c.name} [${language.dir}]: ${it}".toString()) }
            }
        }
        return result
    }

}
