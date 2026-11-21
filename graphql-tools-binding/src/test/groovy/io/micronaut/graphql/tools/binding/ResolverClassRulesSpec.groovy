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
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.model.AnnotationView
import io.micronaut.graphql.tools.binding.internal.model.JParameter
import io.micronaut.graphql.tools.binding.internal.model.Nullability
import io.micronaut.graphql.tools.binding.internal.model.TypeRef
import spock.lang.Specification

class ResolverClassRulesSpec extends Specification {

    private static final String DFE = 'graphql.schema.DataFetchingEnvironment'

    private static List<String> schema(String... names) {
        names as List
    }

    private static final String DATA_LOADER = 'org.dataloader.DataLoader'

    private static JParameter loader(String name, String... typeArguments) {
        def type = new TypeRef(DATA_LOADER, typeArguments.collect { TypeRef.of(it) }, Nullability.UNKNOWN, false, false)
        [name: { name }, nameIsSynthetic: { false }, type: { type }, annotations: { AnnotationView.none() }] as JParameter
    }

    void "a DataLoader parameter is no argument: it binds in any position after the source, and the others still bind by name"() {
        when:
        def verdict = ResolverClassRules.arguments('a.Book',
                [FakeModel.parameter('book', 'a.Book'), loader('loader', 'java.lang.String', 'a.Seller'),
                 FakeModel.parameter('genre', 'java.lang.String'), loader('other', 'java.lang.Long', 'java.lang.String'),
                 FakeModel.parameter('env', DFE)], schema('genre'))

        then:
        verdict.failure() == null
        verdict.slots()*.kind() == [SlotKind.SOURCE, SlotKind.DATA_LOADER, SlotKind.ARGUMENT, SlotKind.DATA_LOADER, SlotKind.ENVIRONMENT]
        verdict.slots()*.parameter() == [0, 1, 2, 3, 4]
    }

    void "a DataLoader without its key and value types fails, raw or with Object for both"() {
        when:
        def raw = ResolverClassRules.arguments(null, [loader('loader')], schema())
        def objects = ResolverClassRules.arguments(null, [loader('loader', 'java.lang.Object', 'java.lang.Object')], schema())
        def typed = ResolverClassRules.arguments(null, [loader('loader', 'java.lang.Object', 'java.lang.String')], schema())

        then:
        raw.failure() == new ArgumentVerdict.UntypedDataLoader(0)
        objects.failure() == new ArgumentVerdict.UntypedDataLoader(0)
        typed.failure() == null
    }

    private static JParameter contextValue(String name, String type, String key = '', boolean primitive = false,
                                           boolean synthetic = false) {
        def view = [has: { String it -> it == AnnotationNames.CONTEXT_VALUE },
                    stringValue: { String a, String m ->
                        a == AnnotationNames.CONTEXT_VALUE && m == 'value' ? Optional.of(key) : Optional.empty()
                    },
                    classValues: { String a, String m -> [] }] as AnnotationView
        def ref = new TypeRef(type, [], Nullability.UNKNOWN, primitive, false)
        [name: { name }, nameIsSynthetic: { synthetic }, type: { ref }, annotations: { view }] as JParameter
    }

    void "each injectable kind binds by its type in any position and is no argument of the field"() {
        when:
        def verdict = ResolverClassRules.arguments(null,
                [FakeModel.parameter(name, type), FakeModel.parameter('id', 'java.lang.String')], schema('id'))

        then:
        verdict.failure() == null
        verdict.slots()*.kind() == [kind, SlotKind.ARGUMENT]

        where:
        name        | type                                                | kind
        'env'       | DFE                                                 | SlotKind.ENVIRONMENT
        'context'   | 'graphql.GraphQLContext'                            | SlotKind.CONTEXT
        'selection' | 'graphql.schema.DataFetchingFieldSelectionSet'      | SlotKind.SELECTION_SET
        'locale'    | 'java.util.Locale'                                  | SlotKind.LOCALE
        'principal' | 'java.security.Principal'                           | SlotKind.PRINCIPAL
        'auth'      | 'io.micronaut.security.authentication.Authentication' | SlotKind.PRINCIPAL
        'request'   | 'io.micronaut.http.HttpRequest'                     | SlotKind.REQUEST
        'response'  | 'io.micronaut.http.HttpResponse'                    | SlotKind.RESPONSE
        'mutable'   | 'io.micronaut.http.MutableHttpResponse'             | SlotKind.RESPONSE
    }

    void "an Optional of the response is an injectable kind, an Optional of anything else is an argument"() {
        when:
        def response = new TypeRef('java.util.Optional', [TypeRef.of('io.micronaut.http.MutableHttpResponse')],
                Nullability.UNKNOWN, false, false)
        def other = new TypeRef('java.util.Optional', [TypeRef.of('java.lang.String')], Nullability.UNKNOWN, false, false)
        def optionalResponse = [name: { 'response' }, nameIsSynthetic: { false }, type: { response },
                                annotations: { AnnotationView.none() }] as JParameter
        def optionalOther = [name: { 'id' }, nameIsSynthetic: { false }, type: { other },
                             annotations: { AnnotationView.none() }] as JParameter
        def verdict = ResolverClassRules.arguments(null, [optionalResponse, optionalOther], schema('id'))

        then:
        verdict.failure() == null
        verdict.slots()*.kind() == [SlotKind.OPTIONAL_RESPONSE, SlotKind.ARGUMENT]
    }

    void "a context value binds by the annotation value, else by the parameter name, and its name never collides with an argument"() {
        when:
        def verdict = ResolverClassRules.arguments(null,
                [contextValue('id', 'java.lang.String', 'tenant'), contextValue('region', 'java.lang.String'),
                 FakeModel.parameter('id', 'java.lang.String')], schema('id'))

        then:
        verdict.failure() == null
        verdict.slots()*.kind() == [SlotKind.CONTEXT_VALUE, SlotKind.CONTEXT_VALUE, SlotKind.ARGUMENT]
        verdict.slots()*.argument() == ['tenant', 'region', 'id']
    }

    void "a context value of a primitive type, or without a name in the class file, cannot be satisfied"() {
        when:
        def primitive = ResolverClassRules.arguments(null, [contextValue('count', 'int', 'count', true)], schema())
        def unnamed = ResolverClassRules.arguments(null, [contextValue('arg0', 'java.lang.String', '', false, true)], schema())
        def named = ResolverClassRules.arguments(null, [contextValue('arg0', 'java.lang.String', 'tenant', false, true)], schema())

        then:
        primitive.failure() instanceof ArgumentVerdict.UnsatisfiableParameter
        primitive.failure().parameter() == 0
        unnamed.failure() instanceof ArgumentVerdict.UnsatisfiableParameter
        named.failure() == null
    }

    void "an injectable parameter after the source is not counted, and a method with too many other parameters still fails"() {
        when:
        def ok = ResolverClassRules.arguments('a.Book',
                [FakeModel.parameter('book', 'a.Book'), FakeModel.parameter('locale', 'java.util.Locale')], schema())
        def tooMany = ResolverClassRules.arguments(null,
                [FakeModel.parameter('locale', 'java.util.Locale'), FakeModel.parameter('x', 'java.lang.String')], schema())

        then:
        ok.failure() == null
        ok.slots()*.kind() == [SlotKind.SOURCE, SlotKind.LOCALE]
        tooMany.failure() == new ArgumentVerdict.CountMismatch(true, 1, 0)
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
