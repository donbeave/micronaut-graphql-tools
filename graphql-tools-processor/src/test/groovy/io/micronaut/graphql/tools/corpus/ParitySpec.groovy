package io.micronaut.graphql.tools.corpus

import spock.lang.Specification

/** The soundness rule of the parity assertion, and the codes that a case may relax at startup. */
class ParitySpec extends Specification {

    static final Finding UNBOUND = new Finding('unbound-resolver-method', 'example.A#stray()')
    static final Finding NOT_INTROSPECTED = new Finding('class-not-introspected', 'Filter')

    private static List<String> check(Verdict compile, Set<String> relaxed) {
        Parity.check('case', Language.JAVA, Verdict.bound(), compile, new Divergences(0, []), relaxed)
    }

    void "a compile finding that the startup does not report is a false compile error"() {
        expect:
        check(Verdict.failed([UNBOUND]), [] as Set).size() == 1
    }

    void "a relaxed code is outside the rule, and every other code of the case is still compared"() {
        expect:
        check(Verdict.failed([UNBOUND]), ['unbound-resolver-method'] as Set) == []
        check(Verdict.failed([UNBOUND, NOT_INTROSPECTED]), ['unbound-resolver-method'] as Set).size() == 1
    }

    void "strict=false relaxes the strict checks only, a per-check setting relaxes its code only"() {
        given:
        def strictOff = ['micronaut.graphql.tools.strict': 'false']
        def perCheck = ['micronaut.graphql.tools.checks.unbound-resolver-method': 'warn',
                        'micronaut.graphql.tools.checks.unmapped-input-property': 'fail']
        def strictOn = ['micronaut.graphql.tools.strict': 'true']

        expect:
        'unbound-resolver-method' in Parity.relaxedCodes(strictOff)
        !('class-not-introspected' in Parity.relaxedCodes(strictOff))
        Parity.relaxedCodes(perCheck) == ['unbound-resolver-method'] as Set
        Parity.relaxedCodes(strictOn).empty
    }
}
