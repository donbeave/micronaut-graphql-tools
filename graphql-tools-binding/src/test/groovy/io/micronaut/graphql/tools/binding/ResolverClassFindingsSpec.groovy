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

import graphql.schema.idl.SchemaParser
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.model.AnnotationView
import io.micronaut.graphql.tools.binding.internal.model.JClass
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassFindings
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.graphql.tools.binding.FakeMethods.IGNORED
import static io.micronaut.graphql.tools.binding.FakeMethods.STAGE
import static io.micronaut.graphql.tools.binding.FakeMethods.coordinate
import static io.micronaut.graphql.tools.binding.FakeMethods.method
import static io.micronaut.graphql.tools.binding.FakeMethods.model
import static io.micronaut.graphql.tools.binding.FakeMethods.ref

class ResolverClassFindingsSpec extends Specification {

    static final String SDL = '''\
type Query {
  book(id: ID!): Book
  ping: String!
}
type Mutation {
  ping: String
}
type Book {
  title(upper: Boolean): String
}
'''

    static final String ROOT = AnnotationNames.ROOT_RESOLVER
    static final String TYPE = AnnotationNames.TYPE_RESOLVER
    static final STRING = ref('java.lang.String')

    @Shared
    SchemaModel schema = SchemaModel.load(new SchemaParser().parse(SDL)).get()

    private static AnnotationView annotationsOf(List<String> names, List<String> contracts = []) {
        [has: { String it -> names.contains(it) }, stringValue: { String a, String m -> Optional.empty() },
         classValues: { String a, String m -> a == TYPE ? contracts : [] }] as AnnotationView
    }

    private static JClass resolver(List methods, List<String> annotations, List<String> contracts = [], List<JClass> supertypes = []) {
        [name: { 'a.R' }, annotations: { annotationsOf(annotations, contracts) }, bindableMethods: { methods },
         supertypesAnnotatedWith: { String it -> supertypes }] as JClass
    }

    private List<String> check(JClass resolver) {
        ResolverClassFindings.check(schema, model(), resolver).collect { it.code() + ' ' + it.coordinate() }
    }

    void "a root method that carries the name of no root field is unbound, a helper with a hatch or a lifecycle is not"() {
        given:
        def methods = [
                method('ping', [:], [], STRING, 'a.R#ping()'),
                method('stray', [:], [FakeModel.parameter('x', 'java.lang.String')], STRING, 'a.R#stray(java.lang.String)'),
                method('hidden', IGNORED, [], STRING, 'a.R#hidden()'),
                method('onStart', ['io.micronaut.runtime.event.annotation.EventListener': [:]], [], STRING, 'a.R#onStart()'),
        ]

        expect:
        check(resolver(methods, [ROOT])) == ['root-method-several-operations a.R#ping()', 'unbound-resolver-method a.R#stray(java.lang.String)']
    }

    void "a root method that binds the root fields of several operations is reported once, with the coordinates"() {
        given:
        def findings = ResolverClassFindings.check(schema, model(), resolver([method('ping')], [ROOT]))

        expect:
        findings.size() == 1
        findings[0].sentence().contains('Mutation.ping, Query.ping')
        findings[0].member() == 'a.R#ping()'
    }

    void "the arguments are matched by name, and a violation names the coordinate"() {
        given:
        def methods = [
                method('book', [:], [FakeModel.parameter('limit', 'int')], STRING, 'a.R#book(int)'),
                method('ping', coordinate('Query.ping'), [FakeModel.parameter('x', 'int')], STRING, 'a.R#ping(int)'),
        ]
        def findings = ResolverClassFindings.check(schema, model(), resolver(methods, [ROOT]))

        expect:
        findings.collect { it.code() + ' ' + it.coordinate() } == ['argument-mismatch Query.ping']
    }

    void "a parameter that names no argument is argument-mismatch, with the arguments of the field"() {
        given:
        def findings = ResolverClassFindings.check(schema, model(),
                resolver([method('book', [:], [FakeModel.parameter('limit', 'int')], STRING, 'a.R#book(int)')], [ROOT]))

        expect:
        findings*.code() == ['argument-mismatch']
        findings[0].coordinate() == 'Query.book'
        findings[0].sentence() == 'the parameter limit binds no argument of Query.book (arguments: id)'
    }

    void "a return type that the field cannot take is unsupported-return-type, after the arguments"() {
        given:
        def methods = [method('book', [:], [FakeModel.parameter('id', 'java.lang.String')],
                ref(STAGE, ref(STAGE, ref('a.Book'))), 'a.R#book(String)')]

        expect:
        check(resolver(methods, [ROOT])) == ['unsupported-return-type Query.book']
    }

    void "a coordinate that does not fit the schema or the method is unknown-schema-coordinate, and nothing else is said of the method"() {
        given:
        def methods = [
                method('missing', coordinate('Nope.missing'), [], STRING, 'a.R#missing()'),
                method('book', coordinate('Query.other'), [FakeModel.parameter('limit', 'int')], STRING, 'a.R#book(int)'),
                method('ping', coordinate('Query.ping'), [], STRING, 'a.R#ping()'),
        ]

        expect:
        check(resolver(methods, [ROOT])) == ['unknown-schema-coordinate Nope.missing', 'unknown-schema-coordinate Query.other']
    }

    void "a root type on a type resolver is an invalid coordinate"() {
        expect:
        check(resolver([method('ping', coordinate('Query.ping'))], [TYPE], ['a.Book'])) == ['unknown-schema-coordinate Query.ping']
    }

    void "the source of a type resolver method is the contract, the first parameter"() {
        given:
        def methods = [
                method('title', coordinate('Book.title'), [FakeModel.parameter('other', 'a.Other'), FakeModel.parameter('upper', 'java.lang.Boolean')],
                        STRING, 'a.R#title(Other,Boolean)'),
        ]

        expect:
        check(resolver(methods, [TYPE], ['a.Book'])) == ['invalid-source-parameter Book.title']
    }

    void "a type resolver method that is a field of no object type is unbound, with one contract only"() {
        given:
        def methods = [method('title', [:], [FakeModel.parameter('b', 'a.Book')], STRING, 'a.R#title(Book)'),
                       method('stray', [:], [FakeModel.parameter('b', 'a.Book')], STRING, 'a.R#stray(Book)')]

        expect:
        check(resolver(methods, [TYPE], ['a.Book'])) == ['unbound-resolver-method a.R#stray(Book)']
        check(resolver(methods, [TYPE], ['a.Book', 'a.Other'])) == []
    }

    void "a method of a type resolver that carries the name of a root field only is unbound: a type resolver cannot bind a root field"() {
        given:
        def methods = [method('ping', [:], [FakeModel.parameter('b', 'a.Book')], STRING, 'a.R#ping(Book)')]

        expect:
        check(resolver(methods, [TYPE], ['a.Book'])) == ['unbound-resolver-method a.R#ping(Book)']
    }

    void "the contracts are those of the class and of its supertypes"() {
        given:
        JClass contract = [name: { 'a.Contract' }, annotations: { annotationsOf([TYPE], ['a.Book']) }] as JClass

        expect:
        ResolverClassFindings.contracts(resolver([], [TYPE], ['a.Book'], [contract])) == ['a.Book'] as Set
        ResolverClassFindings.contracts(resolver([], [], [], [])) == [] as Set
    }

    void "overloads that bind one field are an ambiguity of the startup: the arguments of none of them are judged"() {
        given:
        def methods = [method('book', [:], [FakeModel.parameter('id', 'java.lang.String')], STRING, 'a.R#book(String)'),
                       method('book', [:], [FakeModel.parameter('x', 'int')], STRING, 'a.R#book(int)')]

        expect:
        check(resolver(methods, [ROOT])) == []
    }

    void "a class that is neither a root nor a type resolver has no findings"() {
        expect:
        check(resolver([method('stray')], [])) == []
    }
}
