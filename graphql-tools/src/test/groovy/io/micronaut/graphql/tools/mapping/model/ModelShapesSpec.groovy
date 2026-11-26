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
package io.micronaut.graphql.tools.mapping.model

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import static io.micronaut.graphql.tools.mapping.model.JavaModelShapes.*

class ModelShapesSpec extends AbstractSpec {

    static final String SPEC_NAME = "ModelShapesSpec"

    @Language("GraphQL")
    static final String RECORDS_SCHEMA = """
schema {
  query: Query
}

type Query {
  books(filter: BookFilter): [Book!]!
}

type Author {
  name: String!
}

type Book {
  title: String!
  pages: Int!
  author: Author!
  tags: [String!]!
}

input BookFilter {
  title: String
  minPages: Int
  tags: [String!]
}
"""

    @Language("GraphQL")
    static final String UNION_SCHEMA = """
schema {
  query: Query
}

type Query {
  payloads: [Payload!]!
}

union Payload = Receipt | Failure

type Receipt {
  code: String!
}

type Failure {
  reason: String!
}
"""

    @Language("GraphQL")
    static final String BARE_SCHEMA = """
schema {
  query: Query
}

type Query {
  bare: Bare!
}

type Bare {
  reason: String!
}
"""

    @Language("GraphQL")
    static final String SEALED_INTERFACE_SCHEMA = """
schema {
  query: Query
}

type Query {
  shapes: [Shape!]!
}

interface Shape {
  name: String!
}

type Circle implements Shape {
  name: String!
  radius: Float!
}

type Square implements Shape {
  name: String!
  side: Float!
}
"""

    @Language("GraphQL")
    static final String FLUENT_SCHEMA = """
schema {
  query: Query
}

type Query {
  labelled: [Labelled!]!
}

interface Labelled {
  name: String!
}

type RecordNamed implements Labelled {
  name: String!
  rank: Int!
}

type ClassNamed implements Labelled {
  name: String!
  extra: String!
}
"""

    void "a record output model with a nested record and a list is read through its accessors, and a record input filters it"() {
        given:
        startContext(RECORDS_SCHEMA, SPEC_NAME, ['spec.scenario': 'model-records'])

        when:
        def all = executeQuery('{ books { title pages author { name } tags } }')
        def filtered = executeQuery('{ books(filter: {minPages: 100, tags: ["a"]}) { title } }')
        def titled = executeQuery('{ books(filter: {title: "Second"}) { title pages } }')

        then:
        all.errors.isEmpty()
        all.data.books == [
                [title: 'First', pages: 50, author: [name: 'Ann'], tags: ['a']],
                [title: 'Second', pages: 150, author: [name: 'Bob'], tags: ['a', 'b']]
        ]
        filtered.errors.isEmpty()
        filtered.data.books*.title == ['Second']
        titled.data.books == [[title: 'Second', pages: 150]]
    }

    void "a sealed union of records resolves a member by its exact class and a member by assignability"() {
        given:
        startContext(UNION_SCHEMA, SPEC_NAME, ['spec.scenario': 'model-union'])

        when:
        def result = executeQuery('{ payloads { __typename ... on Receipt { code } ... on Failure { reason } } }')

        then:
        result.errors.isEmpty()
        result.data.payloads == [
                [__typename: 'Receipt', code: 'r-1'],
                [__typename: 'Failure', reason: 'slow'],
                [__typename: 'Failure', reason: 'no']
        ]
    }

    void "a record implementing an interface whose accessors are not fluent fails the startup naming the field"() {
        given:
        startContext(BARE_SCHEMA, SPEC_NAME, ['spec.scenario': 'model-bare'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.FIELD_NOT_BOUND
        e.cause.message.contains('Bare.reason')
    }

    void "a sealed interface with record members resolves each record to its object type"() {
        given:
        startContext(SEALED_INTERFACE_SCHEMA, SPEC_NAME, ['spec.scenario': 'model-sealed-interface'])

        when:
        def result = executeQuery('{ shapes { __typename name ... on Circle { radius } ... on Square { side } } }')

        then:
        result.errors.isEmpty()
        result.data.shapes == [
                [__typename: 'Circle', name: 'c', radius: 1.5d],
                [__typename: 'Square', name: 's', side: 2.0d]
        ]
    }

    void "a non-sealed model interface with fluent accessors is read from a record and from a class"() {
        given:
        startContext(FLUENT_SCHEMA, SPEC_NAME, ['spec.scenario': 'model-fluent'])

        when:
        def result = executeQuery('{ labelled { __typename name ... on RecordNamed { rank } ... on ClassNamed { extra } } }')

        then:
        result.errors.isEmpty()
        result.data.labelled == [
                [__typename: 'RecordNamed', name: 'rec', rank: 7],
                [__typename: 'ClassNamed', name: 'cls', extra: 'extra-cls']
        ]
    }

    @Requires(property = 'spec.scenario', value = 'model-records')
    @Singleton
    @GraphQLRootResolver
    static class RecordsQuery {
        List<Book> books(BookFilter filter) {
            def all = [new Book('First', 50, new Author('Ann'), ['a']), new Book('Second', 150, new Author('Bob'), ['a', 'b'])]
            return all.findAll {
                filter == null || ((filter.title() == null || it.title() == filter.title()) &&
                        (filter.minPages() == null || it.pages() >= filter.minPages()) &&
                        (filter.tags() == null || it.tags().containsAll(filter.tags())))
            }
        }
    }

    @Requires(property = 'spec.scenario', value = 'model-union')
    @Singleton
    @GraphQLRootResolver
    static class UnionQuery {
        List<Payload> payloads() {
            [new Receipt('r-1'), new Timeout('slow', 3), new Denied('no')]
        }
    }

    @Requires(property = 'spec.scenario', value = 'model-union')
    @Factory
    static class UnionDictionary {
        @Bean
        @Singleton
        GraphQLSchemaMappingDictionaryCustomizer dictionary() {
            return { it.registerType('Failure', Failure) } as GraphQLSchemaMappingDictionaryCustomizer
        }
    }

    @Requires(property = 'spec.scenario', value = 'model-bare')
    @Singleton
    @GraphQLRootResolver
    static class BareQuery {
        Bare bare() {
            new BareMember('bare')
        }
    }

    @Requires(property = 'spec.scenario', value = 'model-sealed-interface')
    @Singleton
    @GraphQLRootResolver
    static class ShapesQuery {
        List<Shape> shapes() {
            [new Circle('c', 1.5d), new Square('s', 2.0d)]
        }
    }

    @Requires(property = 'spec.scenario', value = 'model-fluent')
    @Singleton
    @GraphQLRootResolver
    static class FluentQuery {
        List<Labelled> labelled() {
            [new RecordNamed('rec', 7), new ClassNamed('cls')]
        }
    }

}
