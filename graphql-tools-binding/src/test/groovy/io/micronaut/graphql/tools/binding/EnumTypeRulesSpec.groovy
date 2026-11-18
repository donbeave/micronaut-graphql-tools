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

import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.rules.EnumTypeRules
import spock.lang.Specification

class EnumTypeRulesSpec extends Specification {

    void "matching values and constants give an empty verdict"() {
        when:
        def verdict = EnumTypeRules.check(['A', 'B'], FakeModel.enumClass('a.E', [A: [], B: []]))

        then:
        verdict.missingSchemaValues().isEmpty()
        verdict.unboundConstants().isEmpty()
        verdict.ignoredConstants().isEmpty()
    }

    void "a schema value without a constant is missing, sorted"() {
        when:
        def verdict = EnumTypeRules.check(['Z', 'A', 'B'], FakeModel.enumClass('a.E', [B: []]))

        then:
        verdict.missingSchemaValues() == ['A', 'Z']
    }

    void "a constant without a schema value is unbound unless it carries the ignore hatch"() {
        when:
        def verdict = EnumTypeRules.check(['A'],
                FakeModel.enumClass('a.E', [A: [], POETRY: [], DRAFT: [AnnotationNames.IGNORE]]))

        then:
        verdict.unboundConstants() == ['POETRY']
        verdict.ignoredConstants() == ['DRAFT']
    }

    void "the annotations of a constant that has a schema value are never read"() {
        given:
        def reads = 0
        def constant = new io.micronaut.graphql.tools.binding.internal.model.JEnumConstant('A',
                [has: { String it -> reads++; true }, stringValue: { a, m -> Optional.empty() },
                 classValues: { a, m -> [] }] as io.micronaut.graphql.tools.binding.internal.model.AnnotationView)
        def type = [name: { 'a.E' }, enumConstants: { [constant] }] as io.micronaut.graphql.tools.binding.internal.model.JClass

        when:
        EnumTypeRules.check(['A'], type)

        then:
        reads == 0
    }
}
