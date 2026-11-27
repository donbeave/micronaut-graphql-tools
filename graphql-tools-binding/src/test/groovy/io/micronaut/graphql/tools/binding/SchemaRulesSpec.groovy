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
import io.micronaut.graphql.tools.binding.internal.model.ResolverMarkers
import io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates.ResolverKind
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel
import io.micronaut.graphql.tools.binding.internal.schema.TypeExtensions
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules
import spock.lang.Shared
import spock.lang.Specification

class SchemaRulesSpec extends Specification {

    static final String SDL = '''\
schema {
  query: Query
  subscription: Events
}
type Query {
  book(id: ID!): Book
}
type Events {
  bookAdded: Book
}
type Book {
  title: String
}
extend type Book {
  pages: Int
}
input BookInput {
  title: String
}
enum Genre {
  FICTION
  SCIENCE
}
'''

    @Shared
    def registry = new SchemaParser().parse(SDL)

    void "the model lists the root operation types and the subscription type"() {
        when:
        def model = SchemaModel.load(registry).get()

        then:
        model.rootTypes() == [query: 'Query', subscription: 'Events']
        model.rootTypeNames() == ['Events', 'Query'] as Set
        model.subscriptionType().get() == 'Events'
    }

    void "a registry without a schema definition and without a root type has no model"() {
        expect:
        SchemaModel.load(new SchemaParser().parse('type Book { a: Int }')).isEmpty()
    }

    void "a registry without a schema definition takes the implicit root types"() {
        expect:
        SchemaModel.load(new SchemaParser().parse('type Query { a: Int } type Subscription { b: Int }')).get().rootTypes() ==
                [query: 'Query', subscription: 'Subscription']
    }

    void "the model locates a coordinate in the source"() {
        given:
        def model = SchemaModel.load(registry).get()

        expect:
        model.locationOf('Query.book').get().line == 6
        model.locationOf('Genre.SCIENCE').get().line == 22
        model.locationOf('BookInput.title').get().line == 18
        model.locationOf('Book').get().line == 11
        model.locationOf('Query.missing').isEmpty()
        model.locationOf('Missing').isEmpty()
    }

    void "the type extensions are folded into the type"() {
        when:
        def merged = TypeExtensions.merge(registry)

        then:
        merged.getType('Book').get().fieldDefinitions*.name == ['title', 'pages']
        TypeExtensions.objectExtensionFields(registry) == ['Book.pages'] as Set
    }

    void "the scalar table lists the Java classes of each built-in scalar"() {
        expect:
        ScalarCatalog.isGraphQlBuiltInType('Long')
        !ScalarCatalog.isGraphQlBuiltInType('DateTime')
        ScalarCatalog.isJavaBuiltInClass(int)
        !ScalarCatalog.isJavaBuiltInClass(Object)
    }

    void "the catalog answers by class name for the compile-time checks, with a primitive and its wrapper as one class"() {
        expect:
        ScalarCatalog.isKnown('Int') && ScalarCatalog.isKnown('Instant') && !ScalarCatalog.isKnown('DateTime')
        ScalarCatalog.acceptsClassName('Int', 'int') && ScalarCatalog.acceptsClassName('Int', 'java.lang.Integer')
        !ScalarCatalog.acceptsClassName('Int', 'java.lang.Long')
        ScalarCatalog.acceptsClassName('ID', 'java.lang.String') && !ScalarCatalog.acceptsClassName('ID', 'java.lang.Long')
        ScalarCatalog.acceptsClassName('String', 'groovy.lang.GString') && !ScalarCatalog.acceptsClassName('ID', 'groovy.lang.GString')
        ScalarCatalog.acceptsClassName('Instant', 'java.time.Instant') && !ScalarCatalog.acceptsClassName('Instant', 'java.lang.String')
        ScalarCatalog.acceptsClassName('PositiveInt', 'int') && ScalarCatalog.acceptsClassName('PositiveInt', 'java.lang.Integer')
        ScalarCatalog.acceptsClassName('JSON', 'java.util.Map') && ScalarCatalog.acceptsClassName('JSON', 'example.Anything')
        !ScalarCatalog.acceptsClassName('DateTime', 'java.time.Instant')
        ScalarCatalog.acceptedClassNames('Int') == 'Integer or int'
        ScalarCatalog.acceptedClassNames('Instant') == 'Instant'
    }

    void "a String field accepts a GString and an ID field does not"() {
        expect:
        ScalarCatalog.acceptsReturnClass('String', GString)
        ScalarCatalog.acceptsReturnClass('String', String)
        !ScalarCatalog.acceptsReturnClass('ID', GString)
        !ScalarCatalog.acceptsReturnClass('Int', GString)
        !ScalarCatalog.acceptsReturnClass('String', CharSequence)
    }

    void "a bean that mixes root and type contracts is checked per method on the side of its contract"() {
        given:
        def markers = new ResolverMarkers(['query'], null, [
                new ResolverMarkers.ContractMethod(['query'], null, 'book', 1),
                new ResolverMarkers.ContractMethod([], 'example.Book', 'pages', 1)])

        expect:
        SchemaCoordinates.kindsOf(true, true, markers, 'book', 1) == [ResolverKind.ROOT] as Set
        SchemaCoordinates.kindsOf(true, true, markers, 'pages', 1) == [ResolverKind.TYPE] as Set
        SchemaCoordinates.kindsOf(true, true, markers, 'helper', 1) == [ResolverKind.ROOT, ResolverKind.TYPE] as Set
        SchemaCoordinates.kindsOf(true, false, markers, 'pages', 1) == [ResolverKind.ROOT] as Set
        SchemaCoordinates.kindsOf(false, true, ResolverMarkers.NONE, 'pages', 1) == [ResolverKind.TYPE] as Set
    }

    void "a coordinate rule reports #expected"() {
        given:
        def operations = new TreeSet(['Query', 'Events'])

        expect:
        SchemaCoordinates.check(TypeExtensions.merge(registry), operations, coordinate, kinds as Set).orElse(null) == expected

        where:
        coordinate       | method      | kinds                | expected
        'Query.book'     | 'book'      | [ResolverKind.ROOT]  | null
        'Book.pages'     | 'pages'     | [ResolverKind.TYPE]  | null
        'Query book'     | 'book'      | [ResolverKind.ROOT]  | 'the value is not a schema coordinate of the form Type.field.'
        'Missing.x'      | 'x'         | [ResolverKind.ROOT]  | 'the schema has no type Missing.'
        'Genre.FICTION'  | 'FICTION'   | [ResolverKind.TYPE]  | 'Genre is neither an object type nor an interface.'
        'Query.nothing'  | 'nothing'   | [ResolverKind.ROOT]  | 'the type Query has no field nothing.'
        'Query.book'     | 'other'     | [ResolverKind.ROOT]  | null
        'Book.pages'     | 'pages'     | [ResolverKind.ROOT]  | 'a @GraphQLRootResolver binds fields of the root operation types [Events, Query] only, not of Book.'
        'Query.book'     | 'book'      | [ResolverKind.TYPE]  | 'a @GraphQLTypeResolver cannot bind a field of the root operation type Query.'
    }

    void "the wrapper sentences name the wrappers"() {
        expect:
        WrapperRules.cannotWrap('java.util.Optional', 'java.util.Optional').contains('java.util.Optional cannot wrap')
        WrapperRules.missingTypeArgument('java.util.Optional').endsWith('for example Optional<String>.')
    }
}
