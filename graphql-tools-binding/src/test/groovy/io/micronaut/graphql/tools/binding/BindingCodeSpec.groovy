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
package io.micronaut.graphql.tools.binding

import io.micronaut.graphql.tools.binding.internal.finding.Finding
import spock.lang.Specification

class BindingCodeSpec extends Specification {

    static final List<String> CHECKS = [
            'unbound-resolver-method',
            'root-method-several-operations',
            'unbound-enum-constant',
            'unmapped-input-property',
            'nullable-primitive',
            'unreachable-type',
            'unreachable-value-type',
            'unused-dictionary-entry',
            'interface-read-through-implementation',
            'unwired-directive',
    ]

    // The five compile-time checks of the parity set, the per-class failures, and the codes that exist only at compile time
    static final List<String> COMPILE_TIME = [
            'unbound-resolver-method',
            'root-method-several-operations',
            'unbound-enum-constant',
            'unmapped-input-property',
            'nullable-primitive',
            'unknown-schema-coordinate',
            'coordinate-on-non-resolver',
            'argument-mismatch',
            'invalid-source-parameter',
            'unsupported-return-type',
            'unsupported-input-type',
            'input-field-not-settable',
            'kotlin-nullability-mismatch',
            'kotlin-nullability-loose',
            'incorrect-member-mapping',
            'ambiguous-type-class',
            'class-not-introspected',
            'schema-definition-not-provided',
            'schema-invalid',
            'processor-option-unknown',
            'processor-option-invalid',
            'processor-option-removed',
            'processor-schema-not-configured',
            'processor-schema-not-found',
            'processor-internal-error',
            'client-schema-missing',
            'client-schema-invalid',
            'client-operation-not-found',
            'client-operation-ambiguous',
            'client-document-invalid',
            'client-document-too-large',
    ]

    void "no two codes share a token, and every token is lower-case kebab-case"() {
        expect:
        BindingCode.values()*.getCode().toSet().size() == BindingCode.values().length
        BindingCode.values().every { it.getCode() ==~ /[a-z]+(-[a-z]+)*/ }
    }

    void "the ten checks are the checks of the registry, and nothing else is a check"() {
        expect:
        BindingCode.values().findAll { it.isCheck() }*.getCode().sort() == CHECKS.sort()
    }

    void "the compile-time set is exactly the parity set plus the per-class failures and the compile-only codes"() {
        expect:
        BindingCode.values().findAll { it.isCompileTime() }*.getCode().sort() == COMPILE_TIME.sort()
    }

    void "the whole-program checks run at startup only"() {
        expect:
        ['unreachable-type', 'unreachable-value-type', 'unused-dictionary-entry', 'interface-read-through-implementation',
         'unwired-directive', 'precedence-ambiguity'].every { !BindingCode.fromCode(it).isCompileTime() }
    }

    void "every code has a summary sentence for the error reference"() {
        expect:
        BindingCode.values().every { it.getSummary().endsWith('.') && it.getSummary().length() > 10 }
    }

    void "a code is found by its token, and an unknown token is not"() {
        expect:
        BindingCode.values().every { BindingCode.fromCode(it.getCode()).is(it) }
        BindingCode.fromCode('strict-mode') == null
    }

    void "tag puts the code in square brackets in front of the text"() {
        expect:
        BindingCode.UNBOUND_ENUM_CONSTANT.tag('The constant has no value.') == '[unbound-enum-constant] The constant has no value.'
    }

    void "a finding must carry a code of the registry"() {
        when:
        Finding.of('unbound-method', null, 'text')

        then:
        thrown(IllegalArgumentException)
    }
}
