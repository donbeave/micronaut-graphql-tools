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

    void "a warning is not a compile error: no soundness violation, and the startup finding counts as seen"() {
        given:
        def warning = new Finding('class-not-introspected', 'Filter', '-', true)

        expect:
        Parity.check('case', Language.JAVA, Verdict.bound(), Verdict.failed([warning]), new Divergences(0, [])) == []
        Parity.check('case', Language.JAVA, Verdict.failed([new Finding('class-not-introspected', 'Filter')]), Verdict.failed([warning]),
                new Divergences(0, [])) == []
        Parity.check('case', Language.JAVA, Verdict.failed([new Finding('class-not-introspected', 'Filter')]), Verdict.bound(),
                new Divergences(0, [])).size() == 1
    }

    void "a warning line is parsed and written back, and sorts with the other lines"() {
        expect:
        Finding.parse('warning class-not-introspected Filter schema.graphqls:9:1').warning
        Finding.parse('warning class-not-introspected Filter schema.graphqls:9:1').line() == 'warning class-not-introspected Filter schema.graphqls:9:1'
        !Finding.parse('class-not-introspected Filter schema.graphqls:9:1').warning
        Verdict.failed([new Finding('class-not-introspected', 'Filter', '-', true)]).bound
        !Verdict.failed([new Finding('class-not-introspected', 'Filter', '-', true), UNBOUND]).bound
    }

    void "the documented soundness exceptions excuse exactly the listed error, in the listed case and language"() {
        given:
        def exceptions = new SoundnessExceptions(1, [new SoundnessExceptions.Entry('case', 'java', UNBOUND.code, UNBOUND.coordinate,
                'customizer-claimed-field')])
        def other = new Finding('argument-mismatch', 'Query.a')

        expect:
        Parity.check('case', Language.JAVA, Verdict.bound(), Verdict.failed([UNBOUND]), new Divergences(0, []), [] as Set, exceptions) == []
        Parity.check('case', Language.JAVA, Verdict.bound(), Verdict.failed([UNBOUND, other]), new Divergences(0, []), [] as Set,
                exceptions).size() == 1
        Parity.check('case', Language.GROOVY, Verdict.bound(), Verdict.failed([UNBOUND]), new Divergences(0, []), [] as Set,
                exceptions).size() == 1
        Parity.check('other', Language.JAVA, Verdict.bound(), Verdict.failed([UNBOUND]), new Divergences(0, []), [] as Set,
                exceptions).size() == 1
    }

    void "the corpus lists each documented reason at least once, and every entry of the list has a reason and a live case"() {
        given:
        def corpus = new Corpus(CorpusFixtures.root())

        expect:
        corpus.exceptions.problems == []
        corpus.exceptions.verify(corpus) == []
        corpus.exceptions.entries*.reason.toSet() == SoundnessExceptions.REASONS.keySet()
        and: 'each entry sits on the case that shows its reason, so a reason cannot excuse a finding of an unrelated case'
        corpus.exceptions.entries.every { it.caseName == 'exception/' + it.reason }
    }
}
