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

import io.micronaut.graphql.tools.binding.internal.rules.InputClassCoverage
import spock.lang.Specification

class InputClassCoverageSpec extends Specification {

    private static final book = FakeModel.inputClass('a.Book',
            [FakeModel.property('id'), FakeModel.property('title')])

    void "the input types of one class cover its properties together"() {
        given:
        def coverage = new InputClassCoverage()
        coverage.add('CreateBook', ['title'], book)
        coverage.add('RenameBook', ['id', 'title'], book)

        expect:
        coverage.verdicts([]).every { it.verdict().unmappedProperties().isEmpty() }
    }

    void "a property that no input type of the class covers is reported once, at the smallest type name"() {
        given:
        def coverage = new InputClassCoverage()
        coverage.add('RenameBook', ['title'], book)
        coverage.add('CreateBook', ['title'], book)

        when:
        def verdicts = coverage.verdicts([])

        then:
        verdicts.size() == 1
        verdicts[0].inputType() == 'CreateBook'
        verdicts[0].inputTypes() == ['CreateBook', 'RenameBook']
        verdicts[0].verdict().unmappedProperties() == ['id']
    }

    void "the fields of types that may be bound to the class cover a property too"() {
        given:
        def coverage = new InputClassCoverage()
        coverage.add('CreateBook', ['title'], book)

        expect:
        coverage.verdicts(['id'])[0].verdict().unmappedProperties().isEmpty()
    }

    void "classes are judged apart"() {
        given:
        def coverage = new InputClassCoverage()
        coverage.add('CreateBook', ['title'], book)
        coverage.add('CreateAuthor', ['name'], FakeModel.inputClass('a.Author', [FakeModel.property('name'), FakeModel.property('id')]))

        expect:
        coverage.verdicts([])*.verdict()*.unmappedProperties() == [['id'], ['id']]
    }
}
