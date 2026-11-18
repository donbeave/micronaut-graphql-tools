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

import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.SlotKind
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules.SchemaArgument
import spock.lang.Specification

class ResolverClassRulesSpec extends Specification {

    private static final String DFE = 'graphql.schema.DataFetchingEnvironment'

    private static List<String> schema(String... names) {
        names as List
    }

    void "parameters bind the arguments by name, the environment last"() {
        when:
        def verdict = ResolverClassRules.arguments(null,
                [FakeModel.parameter('genre', 'java.lang.String'), FakeModel.parameter('env', DFE)], schema('genre'))

        then:
        verdict.failure() == null
        verdict.slots()*.kind() == [SlotKind.ARGUMENT, SlotKind.ENVIRONMENT]
        verdict.slots()*.argument() == ['genre', null]
        verdict.slots()*.parameter() == [0, 1]
    }

    void "the source parameter comes first for a type resolver method"() {
        when:
        def verdict = ResolverClassRules.arguments('a.Book',
                [FakeModel.parameter('book', 'a.Book'), FakeModel.parameter('upper', 'boolean')], schema('upper'))

        then:
        verdict.failure() == null
        verdict.slots()*.kind() == [SlotKind.SOURCE, SlotKind.ARGUMENT]
    }

    void "a first parameter of another type is not the source"() {
        when:
        def verdict = ResolverClassRules.arguments('a.Book',
                [FakeModel.parameter('other', 'a.Other')], schema())

        then:
        verdict.failure() == new ArgumentVerdict.InvalidSource(0)
        verdict.slots().isEmpty()
    }

    void "@GraphQLArgument names the argument a parameter binds"() {
        when:
        def verdict = ResolverClassRules.arguments(null,
                [FakeModel.parameter('in', 'java.lang.String', 'input')], schema('input'))

        then:
        verdict.failure() == null
        verdict.slots()*.argument() == ['input']
    }

    void "a parameter that names no argument fails after the slots before it"() {
        when:
        def verdict = ResolverClassRules.arguments(null,
                [FakeModel.parameter('genre', 'java.lang.String'), FakeModel.parameter('limit', 'int')],
                schema('genre', 'page'))

        then:
        verdict.slots()*.argument() == ['genre']
        verdict.failure() == new ArgumentVerdict.ParameterWithoutArgument(1, 'limit', ['genre', 'page'], false)
    }

    void "a parameter name that the class file did not carry is reported as such, unless @GraphQLArgument names the argument"() {
        when:
        def unnamed = ResolverClassRules.arguments(null,
                [FakeModel.parameter('arg0', 'java.lang.String', null, true)], schema('genre'))
        def renamed = ResolverClassRules.arguments(null,
                [FakeModel.parameter('arg0', 'java.lang.String', 'genre', true)], schema('genre'))
        def misspelled = ResolverClassRules.arguments(null,
                [FakeModel.parameter('arg0', 'java.lang.String', 'gnere', true)], schema('genre'))
        def otherIndex = ResolverClassRules.arguments(null,
                [FakeModel.parameter('arg5', 'java.lang.String', null, true)], schema('genre'))

        then:
        unnamed.failure() == new ArgumentVerdict.ParameterWithoutArgument(0, 'arg0', ['genre'], true)
        renamed.failure() == null
        renamed.slots()*.argument() == ['genre']
        misspelled.failure() == new ArgumentVerdict.ParameterWithoutArgument(0, 'gnere', ['genre'], false)
        otherIndex.failure() == new ArgumentVerdict.ParameterWithoutArgument(0, 'arg5', ['genre'], false)
    }

    void "two parameters that bind one argument fail"() {
        when:
        def verdict = ResolverClassRules.arguments(null,
                [FakeModel.parameter('genre', 'java.lang.String'), FakeModel.parameter('other', 'java.lang.String', 'genre')],
                schema('genre', 'page'))

        then:
        verdict.failure() == new ArgumentVerdict.DuplicateParameter('genre')
    }

    void "a wrong parameter count is reported with both counts"() {
        when:
        def verdict = ResolverClassRules.arguments('a.Book',
                [FakeModel.parameter('book', 'a.Book')], schema('upper'))

        then:
        verdict.failure() == new ArgumentVerdict.CountMismatch(false, 1, 2)
    }

    void "the suggested method names the source, then each argument"() {
        expect:
        ResolverClassRules.suggestedMethod('title', 'Book', 'a.Book', [new SchemaArgument('upper', 'Boolean')]) ==
                'title(a.Book book, Boolean upper)'
        ResolverClassRules.suggestedMethod('ping', 'Query', null, []) == null
    }

    void "too many parameters are reported as too many"() {
        when:
        def verdict = ResolverClassRules.arguments(null, [FakeModel.parameter('x', 'int')], schema())

        then:
        verdict.failure() == new ArgumentVerdict.CountMismatch(true, 1, 0)
    }

    void "a field without arguments and a method without parameters need no slot"() {
        expect:
        ResolverClassRules.arguments(null, [], schema()).slots().isEmpty()
    }
}
