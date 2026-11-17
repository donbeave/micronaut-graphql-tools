package io.micronaut.graphql.tools.mapping.returntype

import graphql.ExecutionResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.UnsupportedReturnTypeException
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Fields of the subscription type return a {@link Publisher} of events (graphql-java's subscription contract). The
 * mapping checks the event type; a {@link Publisher} anywhere else fails at startup.
 */
class PublisherReturnTypeSpec extends AbstractTest {

    static final String SPEC_NAME = "PublisherReturnTypeSpec"
    static final String SPEC_NAME_QUERY = "PublisherReturnTypeSpecQuery"
    static final String SPEC_NAME_MUTATION = "PublisherReturnTypeSpecMutation"
    static final String SPEC_NAME_NOT_PUBLISHER = "PublisherReturnTypeSpecNotPublisher"
    static final String SPEC_NAME_IN_LIST = "PublisherReturnTypeSpecInList"
    static final String SPEC_NAME_QUERY_LIST = "PublisherReturnTypeSpecQueryList"
    static final String SPEC_NAME_OBJECT_FIELD = "PublisherReturnTypeSpecObjectField"
    static final String SPEC_NAME_WRAPPED_PUBLISHER = "PublisherReturnTypeSpecWrappedPublisher"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  hello: String
}

type Subscription {
  ticks: Int!
  items: Item!
  asyncNames: String
  wrappedItems: Item
}

type Item {
  name: String!
  label: String!
}
"""

    @Unroll
    void "subscription #subscription emits #expected"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery(subscription)

        then:
        result.errors.isEmpty()
        result.data instanceof Publisher

        when:
        List<ExecutionResult> events = Flux.from((Publisher<ExecutionResult>) result.data)
                .collectList()
                .block(Duration.ofSeconds(10))

        then:
        events*.errors.flatten().isEmpty()
        events*.data == expected

        where:
        subscription                                  | expected
        "subscription { ticks }"                      | [[ticks: 1], [ticks: 2], [ticks: 3]]
        "subscription { items { name label } }"       | [[items: [name: 'a', label: 'a@null']], [items: [name: 'b', label: 'b@null']]]
        "subscription { asyncNames }"                 | [[asyncNames: 'x'], [asyncNames: 'y']]
        "subscription { wrappedItems { name label } }" | [[wrappedItems: [name: 'c', label: 'c@event-c']]]
    }

    @Unroll
    void "a Publisher on a #operation field fails at startup"() {
        given:
        startContext(schema, specName)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message.startsWith("The return type of the method is not supported for this field: " +
                "${Publisher.name} is only allowed on fields of the subscription type.")
        e.cause.mappingContext.graphQlObjectType == objectType
        e.cause.mappingContext.graphQlField == 'events'

        where:
        operation  | objectType | specName           | schema
        'query'    | 'Query'    | SPEC_NAME_QUERY    | """
schema {
  query: Query
}

type Query {
  events: Int
}
"""
        'mutation' | 'Mutation' | SPEC_NAME_MUTATION | """
schema {
  query: Query
  mutation: Mutation
}

type Query {
  hello: String
}

type Mutation {
  events: Int
}
"""
    }

    void "a subscription field that does not return a Publisher fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  hello: String
}

type Subscription {
  ticks: Int
}
"""
        startContext(schema, SPEC_NAME_NOT_PUBLISHER)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message == """The return type of the method is not supported for this field: a field of the subscription type must return ${Publisher.name} (optionally inside a CompletionStage), found ${Integer.name}.
  GraphQL object type: Subscription
  GraphQL field: ticks
  Mapped class: ${NotPublisherSubscription.name}
  Mapped method: ticks()"""
    }

    void "a subscription field that returns a list of Publishers fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  hello: String
}

type Subscription {
  ticks: [Int]
}
"""
        startContext(schema, SPEC_NAME_IN_LIST)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message == """The return type of the method is not supported for this field: a field of the subscription type must return ${Publisher.name} (optionally inside a CompletionStage), found ${List.name}.
  GraphQL object type: Subscription
  GraphQL field: ticks
  Mapped class: ${InListSubscription.name}
  Mapped method: ticks()"""
    }

    void "a Publisher as a list element of a query field fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  numbers: [Int]
}
"""
        startContext(schema, SPEC_NAME_QUERY_LIST)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message == """The return type of the method is not supported for this field: ${Publisher.name} is only allowed on fields of the subscription type.
  GraphQL object type: Query
  GraphQL field: numbers
  Mapped class: ${QueryListOfPublishers.name}
  Mapped method: numbers()"""
    }

    void "a Publisher on a field of an object type fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  item: Item
}

type Item {
  name: String!
  ticks: Int
}
"""
        startContext(schema, SPEC_NAME_OBJECT_FIELD)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message.startsWith("The return type of the method is not supported for this field: " +
                "${Publisher.name} is only allowed on fields of the subscription type.")
        e.cause.mappingContext.graphQlObjectType == 'Item'
        e.cause.mappingContext.graphQlField == 'ticks'
        e.cause.mappingContext.mappedClass == ItemTicksResolver
    }

    void "a DataFetcherResult around the Publisher of a subscription field is not supported"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  hello: String
}

type Subscription {
  ticks: Int
}
"""
        startContext(schema, SPEC_NAME_WRAPPED_PUBLISHER)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message.startsWith("The return type of the method is not supported for this field: a field of the " +
                "subscription type must return ${Publisher.name} (optionally inside a CompletionStage), found " +
                "${DataFetcherResult.name}.")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'hello'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Subscription {
        Publisher<Integer> ticks() {
            return Flux.just(1, 2, 3)
        }

        Flux<Item> items() {
            return Flux.just(new Item('a'), new Item('b'))
        }

        CompletionStage<Publisher<String>> asyncNames() {
            return CompletableFuture.completedFuture(Flux.just('x', 'y'))
        }

        Publisher<DataFetcherResult<Item>> wrappedItems() {
            return Flux.just(DataFetcherResult.<Item> newResult().data(new Item('c')).localContext('event-c').build())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Item)
    static class ItemResolver {
        String label(Item item, DataFetchingEnvironment env) {
            return item.name + '@' + env.getLocalContext()
        }
    }

    @GraphQLType
    static class Item {
        final String name

        Item(String name) {
            this.name = name
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_QUERY)
    @GraphQLRootResolver
    static class PublisherQuery {
        Publisher<Integer> events() {
            return Flux.just(1)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_MUTATION)
    @GraphQLRootResolver
    static class PublisherMutation {
        String hello() {
            return 'hello'
        }

        Publisher<Integer> events() {
            return Flux.just(1)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NOT_PUBLISHER)
    @GraphQLRootResolver
    static class NotPublisherSubscription {
        String hello() {
            return 'hello'
        }

        Integer ticks() {
            return 1
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_IN_LIST)
    @GraphQLRootResolver
    static class InListSubscription {
        String hello() {
            return 'hello'
        }

        List<Publisher<Integer>> ticks() {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_QUERY_LIST)
    @GraphQLRootResolver
    static class QueryListOfPublishers {
        List<Publisher<Integer>> numbers() {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OBJECT_FIELD)
    @GraphQLRootResolver
    static class ObjectFieldQuery {
        Item item() {
            return new Item('item')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OBJECT_FIELD)
    @GraphQLTypeResolver(Item)
    static class ItemTicksResolver {
        Publisher<Integer> ticks(Item item) {
            return Flux.just(1)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_WRAPPED_PUBLISHER)
    @GraphQLRootResolver
    static class WrappedPublisherSubscription {
        String hello() {
            return 'hello'
        }

        DataFetcherResult<Publisher<Integer>> ticks() {
            return null
        }
    }

}
