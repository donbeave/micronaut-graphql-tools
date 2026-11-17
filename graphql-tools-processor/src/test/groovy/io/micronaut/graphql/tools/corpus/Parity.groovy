package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

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
 * <p>The set of per-class codes is design 2.6 of CT-DESIGN: the five check codes of the parity set (DD-3) and the
 * per-class failures, by the exception name that stands for the stable code until #237 lands.</p>
 */
@CompileStatic
final class Parity {

    static final Set<String> PER_CLASS_CODES = [
            'unbound-resolver-method',
            'root-method-several-operations',
            'unbound-enum-constant',
            'unmapped-input-property',
            'nullable-primitive',
            'InvalidSchemaCoordinateException',
            'IncorrectArgumentCountException',
            'IncorrectArgumentNameException',
            'InvalidSourceArgumentException',
            'UnsupportedReturnTypeException',
            'UnsupportedInputTypeException',
            'InputFieldNotSettableException',
            'ClassNotIntrospectedException',
    ] as Set<String>

    private Parity() {
    }

    /**
     * @param name       the case, for the messages
     * @param language   the language
     * @param runtime    the startup verdict
     * @param compile    the compile-time verdict; {@code null} if none is recorded (read as: reports nothing)
     * @param divergences the known gaps
     * @return the violations, empty if the two verdicts are in parity
     */
    static List<String> check(String name, Language language, Verdict runtime, Verdict compile, Divergences divergences) {
        List<String> violations = []
        Set<String> runtimeKeys = runtime.keys()
        Set<String> compileKeys = compile == null ? [] as Set<String> : compile.keys()
        (compileKeys - runtimeKeys).sort().each {
            violations.add("soundness: the compile side reports '${it}' but the startup does not (a false compile error)".toString())
        }
        runtime.findings.findAll { it.code in PER_CLASS_CODES && !compileKeys.contains(it.key()) }.each { Finding f ->
            if (!divergences.covers(name, language, f)) {
                violations.add("completeness: the startup reports '${f.key()}' but the compile side does not, and it is not in ${Corpus.DIVERGENCES}".toString())
            }
        }
        return violations
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
                check(c.name, language, runtime, compile, corpus.divergences)
                        .each { result.add("${c.name} [${language.dir}]: ${it}".toString()) }
            }
        }
        return result
    }

}
