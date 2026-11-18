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
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules
import spock.lang.Specification

class InputTypeRulesSpec extends Specification {

    void "every settable property that an input field names is mapped"() {
        when:
        def verdict = InputTypeRules.check(['title', 'pages'],
                FakeModel.inputClass('a.BookInput', [FakeModel.property('title'), FakeModel.property('pages')]))

        then:
        verdict.unmappedProperties().isEmpty()
        verdict.ignoredProperties().isEmpty()
    }

    void "a settable property that no input field names is unmapped, sorted"() {
        when:
        def verdict = InputTypeRules.check(['title'], FakeModel.inputClass('a.BookInput',
                [FakeModel.property('title'), FakeModel.property('zeta'), FakeModel.property('alpha')]))

        then:
        verdict.unmappedProperties() == ['alpha', 'zeta']
    }

    void "the ignore hatch excludes a property, and is recorded with its class"() {
        when:
        def verdict = InputTypeRules.check(['title'], FakeModel.inputClass('a.BookInput',
                [FakeModel.property('title'), FakeModel.property('internal', AnnotationNames.IGNORE)]))

        then:
        verdict.unmappedProperties().isEmpty()
        verdict.ignoredProperties() == ['a.BookInput#internal']
    }

    void "a property that an input field names is mapped even when it carries the hatch"() {
        when:
        def verdict = InputTypeRules.check(['title'], FakeModel.inputClass('a.BookInput',
                [FakeModel.property('title', AnnotationNames.IGNORE)]))

        then:
        verdict.unmappedProperties().isEmpty()
        verdict.ignoredProperties().isEmpty()
    }
}
