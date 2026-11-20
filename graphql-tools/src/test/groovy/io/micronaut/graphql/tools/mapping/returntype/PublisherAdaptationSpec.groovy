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
package io.micronaut.graphql.tools.mapping.returntype

import jakarta.inject.Singleton
import graphql.ExecutionResult
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.util.concurrent.atomic.AtomicInteger

class PublisherAdaptationSpec extends AbstractTest {

    static final String SPEC_NAME = "PublisherAdaptationSpec"
    static final String SPEC_NAME_PROPERTY = "PublisherAdaptationSpecProperty"

    static final AtomicInteger SUBSCRIPTIONS = new AtomicInteger()
    static final AtomicInteger CANCELLATIONS = new AtomicInteger()

    @Language("GraphQL")
    static final String SCHEMA = """
type Query {
  one(id: Int!): Item
  none: Item
  failing: Item
  many: [Item!]!
  noMany: [Item!]!
  failingMany: [Item]
  nested: [[Int]]
  nonNullNone: Item!
  plain: String
  counted: Item
}

type Mutation {
  rename(name: String!): Item
  renameAll(names: [String!]!): [Item!]!
}

type Item {
  name: String!
  label: String
  tags: [String!]!
}
"""

    @Language("GraphQL")
    static final String PROPERTY_SCHEMA = """
type Query {
  holder: Holder
}

type Holder {
  name: String
}
"""

    void setup() {
        SUBSCRIPTIONS.set(0)
        CANCELLATIONS.set(0)
    }

    void "a Mono is adapted to its value on a query field, with its arguments"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('{ one(id: 7) { name label tags } }')

        then:
        result.errors.isEmpty()
        result.data == [one: [name: 'item-7', label: 'label-item-7', tags: ['a', 'b']]]
    }

    void "a Mono is adapted on a mutation field"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('mutation { rename(name: "x") { name } }')

        then:
        result.errors.isEmpty()
        result.data == [rename: [name: 'x']]
    }

    void "a Flux is collected into the list of a list field, in order, also on a mutation field"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult query = executeQuery('{ many { name } }')
        ExecutionResult mutation = executeQuery('mutation { renameAll(names: ["a", "b", "c"]) { name } }')

        then:
        query.errors.isEmpty()
        query.data == [many: [[name: 'm1'], [name: 'm2'], [name: 'm3']]]
        mutation.errors.isEmpty()
        mutation.data == [renameAll: [[name: 'a'], [name: 'b'], [name: 'c']]]
    }

    void "a Mono of a list and a Flux of lists adapt to the list shapes the field declares"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('{ nested }')

        then:
        result.errors.isEmpty()
        result.data == [nested: [[1, 2], [3]]]
    }

    void "an empty Mono is null, an empty Flux is an empty list"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('{ none { name } noMany { name } }')

        then:
        result.errors.isEmpty()
        result.data == [none: null, noMany: []]
    }

    void "an empty Mono on a non-null field is the usual non-null error"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('{ plain nonNullNone { name } }')

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('non-null')
        result.errors[0].path == ['nonNullNone']
    }

    void "an error signal of a Mono or a Flux is an error of the field, the rest of the response stays"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('{ plain failing { name } failingMany { name } }')

        then:
        result.errors.size() == 2
        result.errors*.path.collect { it as List }.sort { it[0] } == [['failing'], ['failingMany']]
        result.errors*.message.every { it.contains('boom') }
        result.data == [plain: 'plain', failing: null, failingMany: null]
    }

    void "each call subscribes once, and a Mono is cancelled after its value"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        executeQuery('{ counted { name } }')
        executeQuery('{ counted { name } }')

        then:
        SUBSCRIPTIONS.get() == 2
        CANCELLATIONS.get() == 2
    }

    void "a field that is not reactive keeps its plain data fetcher"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        def schema = getGraphQLBean().graphQLSchema
        def queryType = schema.queryType

        expect:
        schema.codeRegistry.getDataFetcher(queryType, queryType.getFieldDefinition('plain')).getClass().simpleName ==
                'MicronautExecutableMethodDataFetcher'
        schema.codeRegistry.getDataFetcher(queryType, queryType.getFieldDefinition('one')).getClass().simpleName ==
                'PublisherAdaptingDataFetcher'
    }

    void "a Publisher on a bean property fails at startup"() {
        given:
        startContext(PROPERTY_SCHEMA, SPEC_NAME_PROPERTY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNSUPPORTED_RETURN_TYPE
        e.cause.message.contains(WrapperRules.publisherOnProperty())
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        Mono<Item> one(int id) {
            return Mono.just(new Item('item-' + id))
        }

        Mono<Item> none() {
            return Mono.empty()
        }

        Mono<Item> failing() {
            return Mono.error(new IllegalStateException('boom'))
        }

        Flux<Item> many() {
            return Flux.just(new Item('m1'), new Item('m2'), new Item('m3'))
        }

        Flux<Item> noMany() {
            return Flux.empty()
        }

        Flux<Item> failingMany() {
            return Flux.concat(Flux.just(new Item('x')), Flux.error(new IllegalStateException('boom')))
        }

        Flux<List<Integer>> nested() {
            return Flux.just([1, 2], [3])
        }

        Mono<Item> nonNullNone() {
            return Mono.empty()
        }

        String plain() {
            return 'plain'
        }

        Mono<Item> counted() {
            return Mono.just(new Item('c')).doOnSubscribe { SUBSCRIPTIONS.incrementAndGet() }
                    .doOnCancel { CANCELLATIONS.incrementAndGet() }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Mutation {
        Mono<Item> rename(String name) {
            return Mono.just(new Item(name))
        }

        Flux<Item> renameAll(List<String> names) {
            return Flux.fromIterable(names).map { new Item(it) }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLTypeResolver(Item)
    static class ItemResolver {
        Mono<String> label(Item item) {
            return Mono.just('label-' + item.name)
        }

        Flux<String> tags(Item item) {
            return Flux.just('a', 'b')
        }
    }

    @GraphQLType
    static class Item {
        final String name

        Item(String name) {
            this.name = name
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_PROPERTY)
    @Singleton
    @GraphQLRootResolver
    static class HolderQuery {
        Holder holder() {
            return new Holder()
        }
    }

    @GraphQLType
    static class Holder {
        Mono<String> name = Mono.just('x')
    }
}
