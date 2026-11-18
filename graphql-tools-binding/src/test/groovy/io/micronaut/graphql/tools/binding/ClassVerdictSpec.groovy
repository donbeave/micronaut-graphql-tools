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
import io.micronaut.graphql.tools.binding.internal.model.JClass
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.graphql.tools.binding.FakeMethods.IGNORED
import static io.micronaut.graphql.tools.binding.FakeMethods.OPTIONAL
import static io.micronaut.graphql.tools.binding.FakeMethods.STAGE
import static io.micronaut.graphql.tools.binding.FakeMethods.coordinate
import static io.micronaut.graphql.tools.binding.FakeMethods.method
import static io.micronaut.graphql.tools.binding.FakeMethods.model
import static io.micronaut.graphql.tools.binding.FakeMethods.ref
import static io.micronaut.graphql.tools.binding.FakeMethods.view

class ClassVerdictSpec extends Specification {

    static final String SDL = '''\
schema {
  query: Query
  mutation: Mutation
}
type Query {
  book(id: ID!): Book
  books: [Book!]
  titles: [Book]
  ping: String!
}
type Mutation {
  ping: String
}
type Book {
  title(upper: Boolean): String
}
'''

    @Shared
    SchemaModel schema = SchemaModel.load(new SchemaParser().parse(SDL)).get()

    private static JClass resolver(String name, List methods, String... annotations) {
        [name: { name }, annotations: { view(annotations.collectEntries { [(it): [:]] }) }, bindableMethods: { methods }] as JClass
    }

    private static final STRING = ref('java.lang.String')

    void "a root resolver binds the root fields that carry the name of a method, and each is checked"() {
        given:
        def methods = [
                method('book', [:], [FakeModel.parameter('id', 'java.lang.String')], ref(STAGE, ref(OPTIONAL, ref('a.Book'))), 'a.R#book(String)'),
                method('ping', [:], [], STRING, 'a.R#ping()'),
                method('unrelated', [:], [], STRING, 'a.R#unrelated()'),
                method('ping', ['io.micronaut.scheduling.annotation.Scheduled': [:]], [], STRING, 'a.R#scheduledPing()'),
        ]

        when:
        def verdict = ResolverClassRules.check(schema, model(), resolver('a.R', methods, AnnotationNames.ROOT_RESOLVER), null)

        then:
        verdict.type() == 'a.R'
        verdict.methods()*.signature() == ['a.R#book(String)', 'a.R#ping()', 'a.R#unrelated()']
        verdict.methods()*.coordinates() == [['Query.book'], ['Mutation.ping', 'Query.ping'], []]
        verdict.methods()[0].fields()*.coordinate() == ['Query.book']
        verdict.methods()[0].fields()[0].arguments().failure() == null
        verdict.methods()[0].fields()[0].returns().depth() == 2
        verdict.methods()[1].fields()*.coordinate() == ['Mutation.ping', 'Query.ping']
        verdict.methods()[2].fields().isEmpty()
    }

    void "a violation is carried by the verdict of the field it belongs to"() {
        given:
        def methods = [method('book', [:], [FakeModel.parameter('limit', 'int')], ref(STAGE, ref('a.Book')), 'a.R#book(int)')]

        when:
        def field = ResolverClassRules.check(schema, model(), resolver('a.R', methods, AnnotationNames.ROOT_RESOLVER), null)
                .methods()[0].fields()[0]

        then:
        field.arguments().failure() == new ArgumentVerdict.ParameterWithoutArgument(0, 'limit', ['id'], false)
        field.returns().failure() == null
    }

    void "a type resolver binds only what a method declares, with the source as the first parameter"() {
        given:
        def methods = [
                method('title', coordinate('Book.title'), [FakeModel.parameter('book', 'a.Book'), FakeModel.parameter('upper', 'java.lang.Boolean')],
                        STRING, 'a.T#title(Book,Boolean)'),
                method('title2', [:], [], STRING, 'a.T#title2()'),
        ]

        when:
        def verdict = ResolverClassRules.check(schema, model(), resolver('a.T', methods, AnnotationNames.TYPE_RESOLVER), 'a.Book')

        then:
        verdict.methods()*.signature() == ['a.T#title(Book,Boolean)']
        verdict.methods()[0].coordinates() == ['Book.title']
        verdict.methods()[0].fields()[0].arguments().failure() == null
        verdict.methods()[0].fields()[0].arguments().slots()*.parameter() == [0, 1]
    }

    void "an ignored method is no candidate, and a coordinate that names no field produces no field verdict"() {
        given:
        def methods = [
                method('book', IGNORED, [], STRING, 'a.R#book()'),
                method('nowhere', coordinate('Query.nowhere'), [], STRING, 'a.R#nowhere()'),
                method('broken', coordinate('nonsense'), [], STRING, 'a.R#broken()'),
        ]

        when:
        def verdict = ResolverClassRules.check(schema, model(), resolver('a.R', methods, AnnotationNames.ROOT_RESOLVER), null)

        then:
        verdict.methods()*.signature() == ['a.R#nowhere()', 'a.R#broken()']
        verdict.methods()*.coordinates() == [['Query.nowhere'], []]
        verdict.methods()*.fields() == [[], []]
    }

    void "a declared coordinate whose field name differs from the method name binds nothing"() {
        given:
        def methods = [method('titleText', coordinate('Book.title'), [FakeModel.parameter('book', 'a.Book')], STRING, 'a.T#titleText(Book)')]

        when:
        def verdict = ResolverClassRules.check(schema, model(), resolver('a.T', methods, AnnotationNames.TYPE_RESOLVER), 'a.Book')

        then:
        verdict.methods()*.coordinates() == [[]]
        verdict.methods()*.fields() == [[]]
    }

    void "the wrappers of a list element are checked one level down, as the runtime does"() {
        given:
        def lists = model(['java.util.List': ['java.lang.Iterable']])
        def methods = [
                method('books', [:], [], ref('java.util.List', ref(STAGE, ref('a.Book'))), 'a.R#books()'),
                method('titles', [:], [], ref(STAGE, ref('java.util.List', ref(OPTIONAL, ref('a.Book')))), 'a.R#titles()'),
        ]

        when:
        def verdict = ResolverClassRules.check(schema, lists, resolver('a.R', methods, AnnotationNames.ROOT_RESOLVER), null)
        def books = verdict.methods()[0].fields()[0].returns()
        def titles = verdict.methods()[1].fields()[0].returns()

        then:
        books.failure() == null
        books.element().failure() == WrapperRules.completionStageInList()
        books.firstFailure() == WrapperRules.completionStageInList()
        titles.depth() == 1
        titles.failure() == null
        titles.element().depth() == 1
        titles.firstFailure() == null
    }

    void "a non-null list element rejects an Optional"() {
        given:
        def lists = model(['java.util.List': ['java.lang.Iterable']])
        def methods = [method('books', [:], [], ref('java.util.List', ref(OPTIONAL, ref('a.Book'))), 'a.R#books()')]

        when:
        def returns = ResolverClassRules.check(schema, lists, resolver('a.R', methods, AnnotationNames.ROOT_RESOLVER), null)
                .methods()[0].fields()[0].returns()

        then:
        returns.firstFailure() == WrapperRules.optionalAtNonNull('Book!')
    }

    void "a field of the subscription type needs a Publisher"() {
        given:
        def subscription = SchemaModel.load(new SchemaParser().parse('''
schema { query: Query subscription: Events }
type Query { ping: String }
type Events { ping: String }
''')).get()
        def methods = [method('ping', coordinate('Events.ping'), [], STRING, 'a.R#ping()')]

        when:
        def verdict = ResolverClassRules.check(subscription, model(), resolver('a.R', methods, AnnotationNames.ROOT_RESOLVER), null)

        then:
        verdict.methods()[0].fields()[0].returns().failure() == WrapperRules.subscriptionNeedsPublisher('java.lang.String')
    }
}
